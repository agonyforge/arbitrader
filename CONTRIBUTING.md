# Contributing to Arbitrader
Thanks for your interest in contributing!

First of all, please feel free to propose changes to any part of the Arbitrader project with a pull request. If you have questions or have found a bug, please open an issue.

## What Can I Contribute?

### Report Bugs, Suggest Features

#### Before Opening an Issue
Be sure to check the open issues first, to make sure there isn't one that covers the bug you have found.

#### Writing a Good Bug Report
* **Use a short, clear, descriptive title.** Keep it crisp and unique so it's easy to know what the problem is from reading the title.
* **Describe the steps to reproduce** the problem. Include relevant parts of your configuration file (but **NOT** your API keys!) and your log messages that show any errors or stack traces.

#### Writing a Good Feature Request
* **Use a short, clear, descriptive title.** Keep it to a phrase that gives the gist of what your request is.
* **Clearly explain the need** for your new feature. Why would it help? What problem does it solve? In what ways would it help you?
* **Describe how it would work.** What does it do? Does it need any new configuration?

#### After Opening an Issue
Be prepared to discuss your issue, and be open to suggestions or alternatives. The project maintainer(s) reserve the right to accept or reject suggestions based on how they align with the project vision. 

### Add Exchanges
Testing new exchanges is always useful. If you have added a new exchange and it works for you, please consider submitting a pull request back to the project so others can use it too. Here's a quick checklist of what you need to do.

#### How to Add a New Exchange
1. Add the new exchange dependency in `build.gradle`.
1. Add the configuration for the new exchange in `application.yaml`.
1. Compile, run and watch the bot make some trades using the new exchange.
1. Once you are confident that it is working correctly, submit a pull request.

#### What Goes in the Pull Request
1. The new dependency in `build.gradle`.
1. An example configuration in `application.example.yaml` with good descriptions of any unusual parameters (e.g. the "passphrase" for Coinbase Pro). **DO NOT** include your API keys!
1. Any other code you had to change to make the exchange work.

### New Features
Adding new features, fixing bugs and updating documentation is always appreciated. If you aren't sure what to work on, check the open issues and look for anything labeled "Good First Issue". Those tend to be fairly small, well understood and straightforward to implement. Anything labeled "Help Wanted" might be a little more complex but would be particularly useful to have done. Or, if there's something else you want to change about Arbitrader, go ahead and do it!

When you've decided what you'd like to do you can fork the project, open a branch and start working on your changes. When it's ready, open a pull request.

A good pull request will have the following elements.

1. Work in your own fork in a branch, not `master`.
1. Write a detailed explanation of the pull request when you create it.
1. Try to match the code style of the existing code as best as you can.
1. Include documentation if...
    * You added a new feature that has no existing documentation.
    * You changed behavior that is already documented, making the existing docs incorrect.
1. Include tests that cover the code you changed.
