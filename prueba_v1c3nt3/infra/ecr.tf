resource "aws_ecr_repository" "microservice" {
  name                 = "microservice-devops"  # nombre del repo en ECR
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = {
    Project = "prueba-devops"
    Env     = "shared"
  }
}

output "ecr_microservice_url" {
  description = "URL del repositorio ECR para el microservicio"
  value       = aws_ecr_repository.microservice.repository_url
}
