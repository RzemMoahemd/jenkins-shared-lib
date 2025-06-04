def call(Map config) {
    pipeline {
        agent any
        
        environment {
            HTTP_PROXY = "http://squid-proxy.jenkins.svc.cluster.local:3128"
            HTTPS_PROXY = "http://squid-proxy.jenkins.svc.cluster.local:3128"
            NO_PROXY = "localhost,127.0.0.1,.svc.cluster.local"
            SERVICE_NAME = "${config.serviceName}"
            IMAGE_NAME = "${config.imageName}"
            PROJECT_PATH = "${config.projectPath}"
            NEXUS_URL = "http://10.112.62.168:8081/"
        }
        
        
        stages {
            stages {
        stage('Wait for DNS ready') {
            steps {
                script {
                    // Install required network tools
                    sh '''
                        apt-get update
                        apt-get install -y dnsutils iputils-ping curl
                    '''
                    
                    // Verify DNS resolution with retries
                    int maxTries = 5
                    int waitTime = 10
                    for(int i = 0; i < maxTries; i++) {
                        try {
                            sh 'nslookup repo.maven.apache.org'
                            sh 'ping -c 3 repo.maven.apache.org'
                            echo "DNS verification successful"
                            break
                        } catch (Exception e) {
                            echo "DNS not ready yet. Attempt ${i+1}/${maxTries}"
                            sleep(waitTime)
                        }
                    }
                }
            }
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