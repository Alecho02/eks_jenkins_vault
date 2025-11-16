pipeline {
    agent any

    environment {
        APP_DIR = 'prueba_v1c3nt3/app/microservice'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Test') {
            steps {
                dir("${APP_DIR}") {
                    sh 'mvn -B clean test'
                }
            }
        }

        stage('Empaquetar') {
            steps {
                dir("${APP_DIR}") {
                    sh 'mvn -B package -DskipTests'
                }
            }
        }

        stage('Publicar artefacto') {
            steps {
                dir("${APP_DIR}") {
                    archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
                }
            }
        }
    }
}
