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
                // Réparer les permissions
                sh '''
                    chmod 777 /var/run/docker.sock || true
                    docker info
                '''
                
                sh "docker build -t ${IMAGE_NAME}:latest ."
            }
        }
    }
}
            
//             stage('Build Docker Image') {
//     steps {
//         dir(PROJECT_PATH) {
//             script {
//                 // Vérification du socket Docker
//                 sh '''
//                     echo "Vérification de l'accès à Docker:"
//                     ls -l /var/run/docker.sock
//                     docker info
//                 '''
                
//                 // Construction manuelle de l'image
//                 sh "docker build -t ${IMAGE_NAME}:latest ."
//             }
//         }
//     }
// }
            
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
             success {
                echo "Build réussi !"
            }
            failure {
                echo "Échec du build"
            }
        }
    }
}