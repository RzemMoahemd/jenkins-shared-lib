def call(Map config) {
    pipeline {
        agent any
        
        environment {
            SERVICE_NAME = "${config.serviceName}"
            IMAGE_NAME = "${config.imageName}"
            PROJECT_PATH = "${config.projectPath}"
            NEXUS_URL = "10.112.62.168:8081"  // Retirer le schéma http://
            REGISTRY_REPO = "votre-repo-nexus"  // Ajouter le nom du repository Nexus
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
            
            stage('Build and Push Docker Image') {
                agent {
                    kubernetes {
                        defaultContainer 'kaniko'
                        yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: kaniko
    image: gcr.io/kaniko-project/executor:latest
    command: ["/busybox/cat"]
    tty: true
    volumeMounts:
    - name: docker-config
      mountPath: /kaniko/.docker
  volumes:
  - name: docker-config
    configMap:
      name: docker-config  # ConfigMap avec vos credentials
"""
                    }
                }
                steps {
                    dir(PROJECT_PATH) {
                        withCredentials([usernamePassword(
                            credentialsId: 'nexus-credentials',
                            usernameVariable: 'REGISTRY_USER',
                            passwordVariable: 'REGISTRY_PASSWORD'
                        )]) {
                            sh """
                                mkdir -p /kaniko/.docker
                                # Création du fichier de configuration Docker
                                echo "{\"auths\":{\"${NEXUS_URL}\":{\"auth\":\"$(echo -n ${REGISTRY_USER}:${REGISTRY_PASSWORD} | base64)\"}}}" > /kaniko/.docker/config.json
                                
                                # Construction et envoi de l'image
                                /kaniko/executor \
                                  --dockerfile=Dockerfile \
                                  --context=${WORKSPACE}/${PROJECT_PATH} \
                                  --destination=${NEXUS_URL}/${REGISTRY_REPO}/${IMAGE_NAME}:latest \
                                  --insecure \
                                  --skip-tls-verify
                            """
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