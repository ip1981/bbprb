0.3.0 (! BREAKING !)
===================

  * Made it work with the [Rebuild Plugin](https://wiki.jenkins.io/display/JENKINS/Rebuild+Plugin).
  * Job configurations have to be changed. Updated documentation.
  * Require Jenkins 2.60+.
  * Cancel outdated jobs by default.
  * Removed dead code from the Bitbucket API client.


0.2.0
=====

  * Added crumb exclusion for `/bbprb-hook/`.
    Now this plugins works with CSRF protection enabled.

  * Set a pull request's build state to STOPPED when the build is aborted.
    Previously it was FAILED.

  * Inject some useful environment variables e. g. `bbprbDestinationCommitHash`.


0.1.0
=====

  * Can handle creating and updating pull requests.

