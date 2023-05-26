@Library('jenkins-library@feature/DOPS-2406-limit-the-execution-time')

// Job properties
def jobParams = [
  booleanParam(defaultValue: false, description: 'push to the dev profile', name: 'prDeployment'),
]

def pipeline = new org.android.AppPipeline(
    steps:            this,
    sonar:            false,
    pushReleaseNotes: false,
    testCmd:          'runTest',
    dockerImage:      'build-tools/android-build-box-jdk11:latest',
    publishCmd:       'publishReleaseApk',
    jobParams: jobParams,
    appPushNoti: true
    timeoutOption:     '3'
)
pipeline.runPipeline('fearless')
