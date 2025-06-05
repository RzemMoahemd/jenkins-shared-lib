def call(Map config) {
    pipeline {
        agent any
        
        environment {
            HTTP_PROXY = "http://squid-proxy.jenkins:3128"
            HTTPS_PROXY = "http://squid-proxy.jenkins:3128"
            NO_PROXY = "localhost,127.0.0.1,.svc.cluster.local"
            SERVICE_NAME = "${config.serviceName}"
            IMAGE_NAME = "${config.imageName}"
            PROJECT_PATH = "${config.projectPath}"
            NEXUS_URL = "http://10.112.62.168:8081/"
        }
        
        
        stages {
            stage('Wait for DNS ready') {
            steps {
                sh '''
                    for i in {1..5}; do
                        nslookup github.com && nslookup repo.maven.apache.org && break || sleep 5
                    done
                '''
            }
        }

            stage('Checkout') {
                steps {
                    retry(3) {
                        timeout(time: 5, unit: 'MINUTES') {
                            checkout scm
                        }
                    }
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