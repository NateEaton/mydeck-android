## Build Failure - Github Actions

```
Run ./gradlew lintDebug
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/__run_4-1770269449904.json


[Incubating] Problems report is available at: file:///home/runner/work/mydeck-android/mydeck-android/build/reports/problems/problems-report.html
FAILURE: Build failed with an exception.

* What went wrong:
Task 'lintDebug' is ambiguous in root project 'MyDeck' and its subprojects. Candidates are: 'lintAnalyzeGithubReleaseDebug', 'lintAnalyzeGithubReleaseDebugAndroidTest', 'lintAnalyzeGithubReleaseDebugUnitTest', 'lintAnalyzeGithubSnapshotDebug', 'lintAnalyzeGithubSnapshotDebugAndroidTest', 'lintAnalyzeGithubSnapshotDebugUnitTest', 'lintFixGithubReleaseDebug', 'lintFixGithubSnapshotDebug', 'lintGithubReleaseDebug', 'lintGithubSnapshotDebug', 'lintReportGithubReleaseDebug', 'lintReportGithubSnapshotDebug'.

* Try:
> Run gradlew tasks to get a list of available tasks.
> For more on name expansion, please refer to https://docs.gradle.org/8.13/userguide/command_line_interface.html#sec:name_abbreviation in the Gradle documentation.
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.

BUILD FAILED in 1s
Error: Process completed with exit code 1.
```

## Run Tests Failure - Github Actions
```
Error:
Run ./gradlew testGithubReleaseDebugUnitTest testGithubSnapshotDebugUnitTest
Downloading https://services.gradle.org/distributions/gradle-8.13-bin.zip
.............10%.............20%.............30%.............40%.............50%.............60%.............70%.............80%.............90%.............100%
Starting a Gradle Daemon (subsequent builds will be faster)
> Task :app:preBuild UP-TO-DATE
> Task :app:preGithubReleaseDebugBuild UP-TO-DATE
> Task :app:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :app:dataBindingMergeDependencyArtifactsGithubReleaseDebug
> Task :app:generateGithubReleaseDebugResValues
> Task :app:generateGithubReleaseDebugResources
> Task :app:packageGithubReleaseDebugResources
> Task :app:processGithubReleaseDebugNavigationResources
> Task :app:mergeGithubReleaseDebugResources
> Task :app:generateGithubReleaseDebugBuildConfig
> Task :app:parseGithubReleaseDebugLocalResources
> Task :app:checkGithubReleaseDebugAarMetadata
> Task :app:dataBindingGenBaseClassesGithubReleaseDebug
> Task :app:compileGithubReleaseDebugNavigationResources
> Task :app:mapGithubReleaseDebugSourceSetPaths
> Task :app:createGithubReleaseDebugCompatibleScreenManifests
> Task :app:extractDeepLinksGithubReleaseDebug

> Task :app:processGithubReleaseDebugMainManifest
package="com.mydeck.app" found in source AndroidManifest.xml: /home/runner/work/mydeck-android/mydeck-android/app/src/main/AndroidManifest.xml.
Setting the namespace via the package attribute in the source AndroidManifest.xml is no longer supported, and the value is ignored.
Recommendation: remove package="com.mydeck.app" from the source AndroidManifest.xml: /home/runner/work/mydeck-android/mydeck-android/app/src/main/AndroidManifest.xml.

> Task :app:processGithubReleaseDebugManifest
> Task :app:preGithubReleaseDebugUnitTestBuild UP-TO-DATE
> Task :app:javaPreCompileGithubReleaseDebug
> Task :app:preGithubSnapshotDebugBuild UP-TO-DATE
> Task :app:javaPreCompileGithubReleaseDebugUnitTest
> Task :app:dataBindingMergeDependencyArtifactsGithubSnapshotDebug
> Task :app:generateGithubSnapshotDebugResValues
> Task :app:generateGithubSnapshotDebugResources
> Task :app:kspGithubSnapshotDebugUnitTestKotlin
e: file:///home/runner/work/mydeck-android/mydeck-android/app/src/test/java/com/mydeck/app/ui/detail/BookmarkDetailViewModelTest.kt:500:9 No value passed for parameter 'published'.

> Task :app:compileGithubReleaseDebugUnitTestKotlin FAILED

> Task :app:copyRoomSchemas NO-SOURCE
e: file:///home/runner/work/mydeck-android/mydeck-android/app/src/test/java/com/mydeck/app/ui/list/BookmarkListViewModelTest.kt:254:63 Unresolved reference 'toLocalDateTime'.
e: file:///home/runner/work/mydeck-android/mydeck-android/app/src/test/java/com/mydeck/app/ui/list/BookmarkListViewModelTest.kt:1114:13 No value passed for parameter 'readingTime'.
e: file:///home/runner/work/mydeck-android/mydeck-android/app/src/test/java/com/mydeck/app/ui/list/BookmarkListViewModelTest.kt:1114:13 No value passed for parameter 'created'.
e: file:///home/runner/work/mydeck-android/mydeck-android/app/src/test/java/com/mydeck/app/ui/list/BookmarkListViewModelTest.kt:1114:13 No value passed for parameter 'wordCount'.
e: file:///home/runner/work/mydeck-android/mydeck-android/app/src/test/java/com/mydeck/app/ui/list/BookmarkListViewModelTest.kt:1114:13 No value passed for parameter 'published'.
e: file:///home/runner/work/mydeck-android/mydeck-android/app/src/test/java/com/mydeck/app/ui/detail/BookmarkDetailViewModelTest.kt:500:9 No value passed for parameter 'published'.

e: file:///home/runner/work/mydeck-android/mydeck-android/app/src/test/java/com/mydeck/app/ui/list/BookmarkListViewModelTest.kt:254:63 Unresolved reference 'toLocalDateTime'.
> Task :app:compileGithubSnapshotDebugUnitTestKotlin FAILED
e: file:///home/runner/work/mydeck-android/mydeck-android/app/src/test/java/com/mydeck/app/ui/list/BookmarkListViewModelTest.kt:1114:13 No value passed for parameter 'readingTime'.
e: file:///home/runner/work/mydeck-android/mydeck-android/app/src/test/java/com/mydeck/app/ui/list/BookmarkListViewModelTest.kt:1114:13 No value passed for parameter 'created'.
e: file:///home/runner/work/mydeck-android/mydeck-android/app/src/test/java/com/mydeck/app/ui/list/BookmarkListViewModelTest.kt:1114:13 No value passed for parameter 'wordCount'.
e: file:///home/runner/work/mydeck-android/mydeck-android/app/src/test/java/com/mydeck/app/ui/list/BookmarkListViewModelTest.kt:1114:13 No value passed for parameter 'published'.
gradle/actions: Writing build results to /home/runner/work/_temp/.gradle-actions/build-results/__run-1770269193601.json

FAILURE: Build completed with 2 failures.

1: Task failed with an exception.
-----------
* What went wrong:
Execution failed for task ':app:compileGithubReleaseDebugUnitTestKotlin'.
> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
   > Compilation error. See log for more details

* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
72 actionable tasks: 72 executed
> Get more help at https://help.gradle.org.
==============================================================================

2: Task failed with an exception.
-----------
* What went wrong:
Execution failed for task ':app:compileGithubSnapshotDebugUnitTestKotlin'.
> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
   > Compilation error. See log for more details

* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.
==============================================================================

BUILD FAILED in 3m 41s
Error: Process completed with exit code 1.
```