def call(Map config) {
    pipeline {
        agent any
        
        environment {
            SERVICE_NAME = "${config.serviceName}"
            IMAGE_NAME = "${config.imageName}"
            PROJECT_PATH = "${config.projectPath}"
            NEXUS_URL = "http://10.112.62.168:8081/"
        }
        
        stages {
            stage('Checkout') {
                steps {
                    checkout scm
                }
            }
            
            stage('Build') {
                steps {
                    dir(PROJECT_PATH) {
                        sh "mvn clean package -DskipTests"
                    }
                }
            }
            
            stage('Build Docker Image') {
                steps {
                    dir(PROJECT_PATH) {
                        script {
                            docker.build("${IMAGE_NAME}:latest")
                        }
                    }
                }
            }
            
            stage('Push to Nexus') {
                steps {
                    script {
                        docker.withRegistry(NEXUS_URL, 'nexus-credentials') {
                            docker.image("${IMAGE_NAME}:latest").push()
                        }
                    }
                }
            }
        }
        
        post {
            always {
                cleanWs()
            }
        }
    }
}