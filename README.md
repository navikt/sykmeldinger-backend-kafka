[![Deploy to dev and prod](https://github.com/navikt/sykmeldinger-backend-kafka/actions/workflows/deploy.yml/badge.svg?branch=main)](https://github.com/navikt/sykmeldinger-backend-kafka/actions/workflows/deploy.yml)
# sykmeldinger-backend-kafka
This project contains the application code and infrastructure for sykmeldinger-backend-kafka

## Technologies used
* Kotlin
* Ktor
* Gradle
* Kotest

#### Requirements

* JDK 17

## Getting started
### Building the application
#### Compile and package application
To build locally and run the integration tests you can simply run
``` bash 
./gradlew shadowJar
```
or  on windows 
`gradlew.bat shadowJar`

#### Creating a docker image
Creating a docker image should be as simple as
``` bash 
docker build -t sykmeldinger-backend-kafka .
```

#### Running a docker image
``` bash 
docker run --rm -it -p 8080:8080 sykmeldinger-backend-kafka
```

### Upgrading the gradle wrapper
Find the newest version of gradle here: https://gradle.org/releases/ Then run this command:

``` bash 
./gradlew wrapper --gradle-version $gradleVersjon
```

### Contact

This project is maintained by navikt/teamsykmelding

Questions and/or feature requests? Please create an [issue](https://github.com/navikt/sykmeldinger-backend-kafka/issues)

If you work in [@navikt](https://github.com/navikt) you can reach us at the Slack
channel #team-sykmelding
