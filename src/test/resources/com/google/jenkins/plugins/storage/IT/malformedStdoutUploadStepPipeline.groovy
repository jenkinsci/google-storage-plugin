pipeline {
    stages {
        stage('Store to GCS') {
            steps{
                sh '''
                    env > build_environment.txt
                '''
            }
        }
    }
    post {
        always {
            step([$class: 'StdoutUploadStep', credentialsId: env.CREDENTIALS_ID,  bucket: "gs://${env.BUCKET}",
                  logName: env.PATTERN])
        }
    }
}