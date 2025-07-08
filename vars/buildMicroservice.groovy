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





/*
def call(Map config) {
    pipeline {
        agent any
        
        environment {
            //HTTP_PROXY = "http://squid-proxy.jenkins:3128"
            //HTTPS_PROXY = "http://squid-proxy.jenkins:3128"
            //NO_PROXY = "localhost,127.0.0.1,.svc.cluster.local"
            SERVICE_NAME = "${config.serviceName}"
            IMAGE_NAME = "rzem/${config.serviceName}" 
            PROJECT_PATH = "${config.projectPath}"
            NEXUS_URL = "http://10.112.62.168:8081/"
            DOCKERHUB_CREDS = credentials('dockerCredentie')
            KUBECONFIG = credentials('kubeconfig')
        }
        
        stages {

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

      stage('Deploy to Kubernetes') {
    steps {
        script {
            // Se déplacer dans le dossier du microservice puis dans k8s
            dir("${PROJECT_PATH}/k8s") {
                sh "kubectl apply -f deployment.yaml"
                sh "kubectl rollout status deployment/${SERVICE_NAME} --timeout=300s"
                sh "kubectl get pods -l app=${SERVICE_NAME}"
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

*/





def call(Map config) {
    pipeline {
        agent any
        
        
        environment {
            SERVICE_NAME = "${config.serviceName}"
            IMAGE_NAME = "rzem/${config.serviceName}" 
            PROJECT_PATH = "${config.projectPath}"
            DOCKERHUB_CREDS = credentials('dockerhub-cred')
            KUBECONFIG = credentials('kubeconfig')
            NO_PROXY = "192.16.0.233,localhost,127.0.0.1,.svc.cluster.local"
            NAMESPACE = "default"
        }
        
        stages {
            stage('Checkout') {
                steps {
                    retry(3) {
                        timeout(time: 5, unit: 'MINUTES') {
                            checkout scm
                        }
                    }
                 }
            }   
            
            // stage('Build') {
            //     steps {
            //         dir(PROJECT_PATH) {
            //             sh "mvn -s /opt/apache-maven-3.6.3/conf/settings.xml clean package -DskipTests"
            //         }
            //     }
            // }

            stage('Build') {
                steps {
                    retry(3) {
                        timeout(time: 10, unit: 'MINUTES') {
                            dir(PROJECT_PATH) {
                            sh "mvn -U -s /opt/apache-maven-3.6.3/conf/settings.xml clean package -DskipTests"
                            }
                        }
                    }
                }   
            }

            
            // stage('Build Docker Image') {
            //     steps {
            //         dir(PROJECT_PATH) {
            //             script {
            //                 def pom = readMavenPom file: 'pom.xml'
            //                 def artifactId = pom.artifactId
            //                 def version = pom.version
                            
            //                 // Utilisation directe de l'artefact local
            //                 sh "docker build --build-arg ARTIFACT_NAME=target/${artifactId}-${version}.jar -t ${IMAGE_NAME}:latest ."
            //             }
            //         }
            //     }
            // }

            stage('Build Docker Image') {
    steps {
        dir(PROJECT_PATH) {
            script {
                // Récupération de l'artifactId et version via shell
                def artifactId = sh(script: "grep -m1 '<artifactId>' pom.xml | cut -d'>' -f2 | cut -d'<' -f1", returnStdout: true).trim()
                def version = sh(script: "grep -m1 '<version>' pom.xml | cut -d'>' -f2 | cut -d'<' -f1", returnStdout: true).trim()
                
                // Construction de l'image Docker
                sh "docker build --build-arg ARTIFACT_NAME=target/${artifactId}-${version}.jar -t ${IMAGE_NAME}:latest ."
            }
        }
    }
}

            stage('Push to DockerHub') {
                steps {
                    script {
                        withCredentials([
                            usernamePassword(
                                credentialsId: 'dockerhub-cred',
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

            stage('Deploy to Kubernetes') {
                steps {
                    script {
                        dir("${PROJECT_PATH}/k8s") {

                            withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG_FILE')]) {
                                sh """
                                    export no_proxy="${NO_PROXY}"
                                    
                                    # Appliquer les manifests
                                    kubectl --kubeconfig="\$KUBECONFIG_FILE" apply -f deployment.yaml --validate=false
                                    
                                    # Vérifier le déploiement dans le namespace 'default'
                                    kubectl --kubeconfig="\$KUBECONFIG_FILE" -n ${NAMESPACE} rollout status deployment/${SERVICE_NAME} --timeout=300s
                                    
                                    # Vérifier les pods
                                    kubectl --kubeconfig="\$KUBECONFIG_FILE" -n ${NAMESPACE} get pods -l app=${SERVICE_NAME}
                                """
                            }
                                    
                                   
                                   

                        //     withCredentials([file(credentialsId: 'kubeconfig', variable: 'KUBECONFIG')]) {
                        //         sh """
                                   
                        //             export no_proxy="${NO_PROXY}" 
                                   
                        //             kubectl --kubeconfig="\$KUBECONFIG" cluster-info                                  
                                    
                        //             kubectl --kubeconfig="\$KUBECONFIG" apply -f deployment.yaml --validate=false
                        //         """
                        //     }
                        //     //sh "unset http_proxy"
                        //     //sh "unset https_proxy"
                        //    // sh "kubectl apply -f deployment.yaml --kubeconfig=${KUBECONFIG} --context=master-node"
                        //     //sh "kubectl --server=https://192.16.0.233:6443 apply -f deployment.yaml"
                        //     //sh "kubectl --kubeconfig=\${KUBECONFIG} apply -f deployment.yaml --validate=false"
                        //     //sh "kubectl apply -f deployment.yaml --validate=false"
                        //     sh "kubectl rollout status deployment/${SERVICE_NAME} --timeout=300s"
                        //     sh "kubectl get pods -l app=${SERVICE_NAME}"
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