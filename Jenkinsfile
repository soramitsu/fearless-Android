@Library('jenkins-library')

// Job properties
def jobParams = [
  booleanParam(defaultValue: false, description: 'push to the dev profile', name: 'prDeployment'),
]

def pipeline = new org.android.AppPipeline(
    steps:            this,
    sonar:            false,
    pushReleaseNotes: false,
    testCmd:          'runTest',
    dockerImage:      'build-tools/android-build-box-jdk17:latest',
    publishCmd:       'publishReleaseApk',
    jobParams: jobParams,
    appPushNoti: true,
    testBuildTypes: 'PR'
)
pipeline.runPipeline('fearless')
