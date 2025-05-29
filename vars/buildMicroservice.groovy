def call(Map config) {
    pipeline {
        agent any
        
        environment {
            NEXUS_URL = "http://nexus.your-domain.com"  # À remplacer par votre URL Nexus
            SERVICE_NAME = "${config.serviceName}"       # Nom du service passé en paramètre
        }
        
        tools {
            jdk 'jdk17'     # Nom de l'installation JDK dans Jenkins
            maven 'maven3'   # Nom de l'installation Maven dans Jenkins
        }
        
        stages {
            stage('Vérifier DNS') {
                steps {
                    sh '''
                        echo "Vérification de la résolution DNS..."
                        nslookup github.com
                        nslookup ${NEXUS_URL}
                    '''
                }
            }
            
            stage('Checkout Code') {
                steps {
                    checkout scm
                }
            }
            
            stage('Build') {
                steps {
                    sh "mvn clean package -DskipTests"
                }
            }
            
            stage('Tests unitaires') {
                steps {
                    sh "mvn test"
                    junit '**/target/surefire-reports/*.xml'
                }
            }
            
            stage('Analyse SonarQube') {
                when {
                    branch 'main'  # Exécuter seulement sur la branche main
                }
                steps {
                    withSonarQubeEnv('sonarqube') {
                        sh "mvn sonar:sonar -Dsonar.projectName=${SERVICE_NAME}"
                    }
                }
            }
            
            stage('Build Docker Image') {
                steps {
                    script {
                        docker.build("${config.imageName}:${env.BRANCH_NAME}-${env.BUILD_NUMBER}")
                    }
                }
            }
            
            stage('Push vers Nexus') {
                steps {
                    script {
                        docker.withRegistry("${NEXUS_URL}", 'nexus-credentials') {
                            docker.image("${config.imageName}:${env.BRANCH_NAME}-${env.BUILD_NUMBER}").push()
                        }
                    }
                }
            }
            
            stage('Archive Artifact') {
                steps {
                    archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true
                }
            }
        }
        
        post {
            always {
                cleanWs()  # Nettoyer l'espace de travail
            }
            failure {
                emailext (
                    subject: "ÉCHEC du pipeline: ${env.JOB_NAME}",
                    body: "Le build ${env.BUILD_NUMBER} a échoué. Consultez: ${env.BUILD_URL}",
                    to: 'devops@your-company.com'
                )
            }
            success {
                slackSend channel: '#build-success',
                          message: "SUCCÈS: ${env.JOB_NAME} - Build ${env.BUILD_NUMBER}"
            }
        }
    }
}