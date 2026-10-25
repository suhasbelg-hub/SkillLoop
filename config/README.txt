SkillLoop - local database configuration
========================================

This folder holds YOUR local settings. It is deliberately empty of secrets:

  application.properties.example   <- template, safe to copy

Setup:

  copy application.properties.example application.properties
  edit application.properties and set   skillloop.db.password   to the password
  of your MySQL user (skillloop_app)

application.properties is listed in .gitignore, so your password never ends up
in the repository, in a commit or inside an exported archive.

Alternative without any file: set the environment variables
  SKILLLOOP_DB_URL, SKILLLOOP_DB_USER, SKILLLOOP_DB_PASSWORD
