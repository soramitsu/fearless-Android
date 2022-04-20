@Library('jenkins-library')

def pipeline = new org.android.AppPipeline(
    steps:            this,
    sonar:            false,
    pushReleaseNotes: false,
    testCmd:          'runTest',
    dockerImage:      'build-tools/android-build-box-jdk11',
    publishCmd:       'publishReleaseApk'
)
pipeline.runPipeline('fearless')
