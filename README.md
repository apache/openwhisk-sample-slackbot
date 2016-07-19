# Slackbot for OpenWhisk

This repository contains proof-of-concept-quality code to deploy a Slackbot with the capability to run [OpenWhisk](https://github.com/openwhisk/openwhisk) actions.

## Setup

1. Copy `src/main/resources/application.conf.template` to `src/main/resources/application.conf` and fill in the credentials as indicated in the comments.

2. Run `slack.whisk.Main`.

You can run either from `sbt` directly, or use `sbt eclipse` to generate an Eclipse project and create a run configuration from there.

## Usage

(Assuming your bot is called `@whiskbot`.)

Send a run command either as a direct message, or using a mention in a channel where the bot was invited:

    @whiskbot: please run this for me:
    ```function main(args) {
        return { "greeting": "Hello " + args.name + "!" };
    }```
    ```{ "name" : "visitor" }```

The message needs to contain the keywords "run", "please", and two triple-quoted blocks; one for the JavaScript code, and one for the action payload.

## Caveat emptor

* The bot currently only supports JavaScript actions.
* The bot will stop if the websocket connection is lost.
* You must assume users of the bot will be able to gain access to the OpenWhisk credentials.
