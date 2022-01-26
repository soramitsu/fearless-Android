@Library('jenkins-library' ) _

def pipeline = new org.android.AppPipeline(steps: this, sonar: false, testCmd: 'runTest', dockerImage: 'build-tools/android-build-box-jdk11')
pipeline.runPipeline('fearless')
