// def call(Map config) {
//     pipeline {
//         agent any
        
//         environment {
//             HTTP_PROXY = "http://squid-proxy.jenkins:3128"
//             HTTPS_PROXY = "http://squid-proxy.jenkins:3128"
//             NO_PROXY = "localhost,127.0.0.1,.svc.cluster.local"
//             SERVICE_NAME = "${config.serviceName}"
//             IMAGE_NAME = "${config.imageName}"
//             PROJECT_PATH = "${config.projectPath}"
//             NEXUS_URL = "http://10.112.62.168:8081/"
//         }
        
        
//         stages {
//             stage('Wait for DNS ready') {
//             steps {
//                 sh '''
//                     for i in {1..5}; do
//                         nslookup github.com && nslookup repo.maven.apache.org && break || sleep 5
//                     done
//                 '''
//             }
//         }

//             stage('Checkout') {
//                 steps {
//                     retry(3) {
//                         timeout(time: 5, unit: 'MINUTES') {
//                             checkout scm
//                         }
//                     }
//                  }
//             }   
            
//             stage('Build') {
//                 steps {
//                     dir(PROJECT_PATH) {
//                         sh "mvn clean package -DskipTests"
//                     }
//                 }
//             }

//             stage('Build Docker Image') {
//     steps {
//         dir(PROJECT_PATH) {
//             script { 
//                 sh "docker build -t ${IMAGE_NAME}:latest ."
//             }
//         }
//     }
// }
            
// //             stage('Build Docker Image') {
// //     steps {
// //         dir(PROJECT_PATH) {
// //             script {
// //                 // Vérification du socket Docker
// //                 sh '''
// //                     echo "Vérification de l'accès à Docker:"
// //                     ls -l /var/run/docker.sock
// //                     docker info
// //                 '''
                
// //                 // Construction manuelle de l'image
// //                 sh "docker build -t ${IMAGE_NAME}:latest ."
// //             }
// //         }
// //     }
// // }
            
//             stage('Push to Nexus') {
//                 steps {
//                     script {
//                         docker.withRegistry(NEXUS_URL, 'nexus-credentials') {
//                             docker.image("${IMAGE_NAME}:latest").push()
//                         }
//                     }
//                 }
//             }
//         }
        
//         post {
//             always {
//                 cleanWs()
//             }
//              success {
//                 echo "Build réussi !"
//             }
//             failure {
//                 echo "Échec du build"
//             }
//         }
//     }
// }




def call(Map config) {
    pipeline {
        agent any
        
        environment {
            HTTP_PROXY = "http://squid-proxy.jenkins:3128"
            HTTPS_PROXY = "http://squid-proxy.jenkins:3128"
            NO_PROXY = "localhost,127.0.0.1,.svc.cluster.local"
            SERVICE_NAME = "${config.serviceName}"
            IMAGE_NAME = "rzem/${config.serviceName}" 
            PROJECT_PATH = "${config.projectPath}"
            NEXUS_URL = "http://10.112.62.168:8081/"
            DOCKERHUB_CREDS = credentials('dockerCredentiel')
        }
        
        stages {
            // stage('Wait for DNS ready') {
            //     steps {
            //         sh '''
            //             for i in {1..5}; do
            //                 nslookup github.com && nslookup repo.maven.apache.org && break || sleep 5
            //             done
            //         '''
            //     }
            // }

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
                        // Utilisation du settings.xml avec les credentials Nexus
                        sh "mvn -s /opt/apache-maven-3.6.3/conf/settings.xml clean package -DskipTests"
                    }
                }
            }

            stage('Push Artifact to Nexus') {
                steps {
                    dir(PROJECT_PATH) {
                        // Déploiement de l'artefact vers Nexus
                        sh "mvn -s /opt/apache-maven-3.6.3/conf/settings.xml deploy -DskipTests"
                    }
                }
            }

            stage('Build Docker Image') {
                steps {
                    dir(PROJECT_PATH) {
                        script {
                            // Récupération des infos du pom.xml
                            def pom = readMavenPom file: 'pom.xml'
                            def artifactId = pom.artifactId
                            def version = pom.version
                            
                            // Téléchargement de l'artefact depuis Nexus
                           // sh """
                            //    curl -u admin:admin -o target/${artifactId}-${version}.jar \
                             //   ${NEXUS_URL}/repository/maven-snapshots/${pom.groupId.replace('.', '/')}/${artifactId}/${version}/${artifactId}-${version}.jar
                          //  """
                            
                            // Construction de l'image Docker
                            sh "docker build --build-arg ARTIFACT_NAME=${artifactId}-${version}.jar -t ${IMAGE_NAME}:latest ."
                        }
                    }
                }
            }
            
            // stage('Push to Nexus Docker Registry') {
            //     steps {
            //         script {
            //             docker.withRegistry(NEXUS_URL, 'nexus-credentials') {
            //                 docker.image("${IMAGE_NAME}:latest").push()
            //             }
            //         }
            //     }
            // }

            // stage('Push to DockerHub') {
            //     steps {
            //         script {
            //             // Tag pour DockerHub
            //             def dockerHubImage = "rzem/${SERVICE_NAME}:latest"
            //             sh "docker tag ${IMAGE_NAME}:latest ${dockerHubImage}"
                        
            //             // Authentification et push vers DockerHub
            //             withCredentials([usernamePassword(
            //                 credentialsId: 'dockerCredentiel',
            //                 usernameVariable: 'DOCKERHUB_USER',
            //                 passwordVariable: 'DOCKERHUB_PASS'
            //             )]) {
            //                 sh "docker login -u ${env.DOCKERHUB_USER} -p ${env.DOCKERHUB_PASS}"
            //                 sh "docker push ${dockerHubImage}"
            //             }
            //         }
            //     }
            // }


            stage('Push to DockerHub') {
        steps {
          script {
            withCredentials([
              usernamePassword(
                credentialsId: 'dockerCredentiel',
                usernameVariable: 'DOCKERHUB_USER',
                passwordVariable: 'DOCKERHUB_PASS'
              )
            ]) {
              sh '''
                echo "$DOCKERHUB_PASS" | docker login -u $DOCKERHUB_USER --password-stdin
              '''
              sh "docker push ${IMAGE_NAME}:latest"
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