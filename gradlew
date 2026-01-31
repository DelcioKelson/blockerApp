#!/usr/bin/env sh
# Gradle wrapper launcher
BASEDIR=$(dirname "$0")
exec java -cp "$BASEDIR/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
