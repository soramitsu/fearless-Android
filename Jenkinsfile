@Library('jenkins-library')

// Job properties
def jobParams = [
  booleanParam(defaultValue: false, description: 'push to the dev profile', name: 'prDeployment'),
]

def pipeline = new org.android.AppPipeline(
    steps:            this,
    sonar:            true,
    sonarCommand:     './gradlew sonar -x :core-db:compileDebugUnitTestKotlin -x :core-db:compileDebugAndroidTestKotlin -x :feature-crowdloan-impl:compileDebugAndroidTestKotlin -x :runtime:compileDebugUnitTestKotlin -x :app:kaptDebugAndroidTestKotlin -x :app:compileDebugAndroidTestKotlin -Dsonar.coverage.jacoco.xmlReportPaths=**/coverage/*.xml',
    sonarProjectName: 'fearless-android',
    sonarProjectKey:  'fearless:fearless-android',
    pushReleaseNotes: false,
    testCmd:          'runTest --info',
    dockerImage:      'build-tools/android-build-box-jdk17:latest',
    publishCmd:       'publishReleaseApk',
    jobParams:        jobParams,
    appPushNoti:      true,
    dojoProductType:  'android'
)
pipeline.runPipeline('fearless')
