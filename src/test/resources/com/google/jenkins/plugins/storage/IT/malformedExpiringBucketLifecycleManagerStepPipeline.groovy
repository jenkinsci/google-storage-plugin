pipeline {
    stages{
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
            step([$class: 'ExpiringBucketLifecycleManagerStep', credentialsId: env.CREDENTIALS_ID, bucket: "gs://${env.BUCKET}", ttl: 1])

        }
    }
}