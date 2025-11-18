---

# Informe de resultados – Prueba Técnica DevOps

## 1. Resumen ejecutivo

Como parte de la prueba técnica se diseñó e implementó una solución CI/CD sobre AWS que permite:

* Construir un microservicio Java 17.
* Empaquetarlo como imagen Docker y publicarlo en Amazon ECR.
* Desplegarlo automáticamente en un clúster de Kubernetes (EKS) de **development**.
* Gestionar un secreto de aplicación centralizado en **HashiCorp Vault**, consumido desde el pipeline de Jenkins.

La solución se apoyó en **Infraestructura como Código (IaC)** con Terraform para la creación de los clústeres EKS, y en manifiestos de Kubernetes para los despliegues de Jenkins, Vault y del microservicio.

Aunque durante las últimas pruebas se reprovisionó la infraestructura (por temas de laboratorio y costos), la arquitectura y los componentes necesarios quedaron documentados y versionados en el repositorio, de forma que el entorno puede levantarse de nuevo de extremo a extremo.

---

## 2. Arquitectura implementada

### 2.1 Componentes principales

1. **Cluster EKS – deployment**

   * Namespace `jenkins`: aloja el **Jenkins maestro**, con almacenamiento persistente para `/var/jenkins_home`.
   * Namespace `vault`: aloja **HashiCorp Vault** (modo dev para simplicidad en la prueba).
   * Desde este clúster se orquestan los pipelines CI/CD y se consumen los secretos.

2. **Cluster EKS – development**

   * Namespace `microservice`: aloja el microservicio Java.
   * Servicio tipo **LoadBalancer** que expone el microservicio hacia internet para su validación.

3. **Amazon ECR**

   * Repositorio de imágenes Docker del microservicio (`microservice-devops`).

4. **Microservicio Java 17**

   * Proyecto Maven con endpoints que leen:

     * Un valor de configuración local.
     * Un secreto inyectado desde Vault (por variable de entorno).

### 2.2 Infraestructura como Código

* Los clústeres EKS, VPC, subnets, security groups y ECR se definen en Terraform:

  * `terraform/deployment`: recursos asociados al clúster de Jenkins/Vault.
  * `terraform/development`: recursos del clúster de microservicio.
* Esto permite levantar y destruir el entorno de forma reproducible (`terraform apply` / `terraform destroy`).

---

## 3. Pipeline CI/CD

El pipeline se define en un `Jenkinsfile` dentro del repositorio y se ejecuta mediante un **Multibranch Pipeline** conectado a GitHub.

### 3.1 Flujo de etapas

1. **Branch indexing y checkout**

   * Jenkins detecta cambios en el repositorio.
   * Descarga el código en el workspace del pipeline.

2. **Lectura del secreto en Vault**

   * Se configura el **HashiCorp Vault Plugin** en Jenkins:

     * URL del servicio de Vault dentro del cluster (`vault.vault.svc.cluster.local:8200`).
     * Token de acceso gestionado como credencial secreta (`vault-token`).
   * El `Jenkinsfile` utiliza un `vaultSecrets` wrapper para exponer el secreto `APP_SECRET` como variable de entorno.

3. **Build & Tests (Maven)**

   * En un contenedor `maven` dentro de un **pod dinámico** en Kubernetes:

     * `mvn clean test`
     * `mvn package -DskipTests`

4. **Build & Push Docker**

   * El pipeline utiliza contenedores separados para:

     * `aws-cli`: obtener password de ECR.
     * `docker` + `docker:dind`: construir la imagen y subirla a ECR.
   * La imagen se taggea con `build-<BUILD_NUMBER>` y se publica en el repositorio ECR.

5. **Deploy a EKS (development)**

   * Desde el contenedor `aws-cli`:

     * Se ejecuta `aws eks update-kubeconfig` apuntando al clúster `eks-development`.
     * Se aplican los manifiestos Kubernetes del microservicio (`k8s/development/deployment.yaml`).
     * Se actualiza la imagen del deployment con la nueva versión (`kubectl set image`).
     * Se espera a que el rollout finalice correctamente.

---

## 4. Gestión de secretos con Vault

### 4.1 Diseño

* Vault se despliega en un namespace dedicado `vault` mediante Helm.
* Se configura un secrets engine KV con la ruta `secret/microservice`.
* Se almacena el valor sensible bajo la clave `APP_SECRET`.

### 4.2 Consumo desde Jenkins

* Jenkins se comunica con Vault utilizando un token almacenado como credencial.
* El secreto se expone como variable de entorno dentro del pod del pipeline.
* Durante la construcción de la imagen Docker, el valor se pasa como `build-arg` y se define como variable de entorno del contenedor.
* En tiempo de ejecución, el microservicio expone un endpoint que devuelve el secret (solo con fines demostrativos para la prueba).

---

## 5. Resultados de las pruebas

Durante el desarrollo se validaron los siguientes puntos:

1. **Provisionamiento de infraestructura**

   * Creación correcta de VPC, subnets, EKS y ECR mediante Terraform.
   * Conectividad entre Jenkins, Vault y los clústeres.

2. **Ejecución del pipeline**

   * Builds exitosos del microservicio con Maven.
   * Construcción y publicación de la imagen Docker en ECR.
   * Despliegue del microservicio en el cluster de development vía `kubectl set image`.

3. **Validación funcional del microservicio**

   * Acceso al endpoint expuesto por el LoadBalancer del namespace `microservice`.
   * Respuesta del endpoint de configuración local.
   * Respuesta del endpoint que utiliza el secreto proveniente de Vault (comprobación de que la inyección funcionó).

4. **Reprovisionamiento del entorno**

   * Se realizaron pruebas de destrucción y recreación de la infraestructura, comprobando que el entorno vuelve a un estado funcional aplicando Terraform y los manifiestos.

---

## 6. Limitaciones y oportunidades de mejora

Aunque la solución cumple los objetivos principales de la prueba, se identifican varias oportunidades de mejora:

1. **Vault en modo dev**

   * Para la prueba se utilizó modo dev (token único, sin HA).
   * Mejora propuesta: desplegar Vault en modo HA con almacenamiento backend (S3 / DynamoDB) y políticas dedicadas para Jenkins.

2. **Seguridad de red**

   * Actualmente los clústeres y LoadBalancers pueden estar accesibles públicamente.
   * Mejora propuesta:

     * Restringir accesos con security groups y/o ingress controller.
     * Habilitar certificados TLS para Jenkins y el microservicio.

3. **Observabilidad**

   * No se incluyó stack de logs/metrics específicos.
   * Mejora propuesta: integrar CloudWatch Container Insights, Prometheus/Grafana o similar para monitoreo del pipeline y del microservicio.

4. **Automatización completa del bootstrap**

   * Jenkins se configura de forma semi-manual (plugins y jobs).
   * Mejora propuesta: usar `JCasC` (Jenkins Configuration as Code) y seeds jobs para que todo el entorno de CI quede definido como código.

5. **Hardening de IAM**

   * Las credenciales de AWS usadas por Jenkins son suficientemente privilegiadas para la prueba.
   * Mejora propuesta: crear roles IAM específicos con permisos mínimos (least privilege) para ECR, EKS y S3.

---

## 7. Conclusiones

La solución implementada demuestra:

* Uso de **IaC** con Terraform para gestionar la infraestructura de AWS.
* Capacidad para **orquestar pipelines CI/CD** en Jenkins sobre Kubernetes, utilizando pods dinámicos como agentes.
* Integración de **HashiCorp Vault** para la gestión centralizada de secretos, conectando pipeline, Vault y aplicación.
* Despliegue automatizado de un **microservicio Java 17** en EKS, con actualización de imagen y verificación de rollout.

