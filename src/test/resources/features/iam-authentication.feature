Feature: IAM Authentication (US16, US17)

  # US16 – Sign in
  Scenario: Successful sign-in with valid supervisor credentials
    Given a supervisor account exists with loginId "ctrl-sup" and password "Password1!"
    When POST /api/v1/auth/sign-in with {"loginId":"ctrl-sup","password":"Password1!"}
    Then the response status is 200
    And the response body contains accessToken, tokenType "Bearer", role "SUPERVISOR", and expiresAt

  Scenario: Sign-in fails for unknown loginId
    When POST /api/v1/auth/sign-in with {"loginId":"nobody","password":"Password1!"}
    Then the response status is 401
    And the response code is "INVALID_CREDENTIALS"

  Scenario: Sign-in fails for wrong password
    Given a supervisor account exists with loginId "ctrl-sup" and password "Password1!"
    When POST /api/v1/auth/sign-in with {"loginId":"ctrl-sup","password":"WrongPass!"}
    Then the response status is 401
    And the response code is "INVALID_CREDENTIALS"

  Scenario: Sign-in fails for disabled account
    Given a supervisor account exists with loginId "ctrl-sup" and password "Password1!"
    And the account "ctrl-sup" is disabled
    When POST /api/v1/auth/sign-in with {"loginId":"ctrl-sup","password":"Password1!"}
    Then the response status is 401
    And the response code is "INVALID_CREDENTIALS"

  Scenario: Sign-in fails when loginId or password is blank
    When POST /api/v1/auth/sign-in with {"loginId":"","password":""}
    Then the response status is 422
    And the response code is "VALIDATION_FAILED"

  # US17 – Sign out
  Scenario: Successful sign-out with a valid Bearer token
    Given a supervisor account exists with loginId "ctrl-sup" and password "Password1!"
    And the client has signed in and holds a valid Bearer token
    When POST /api/v1/auth/sign-out with the Bearer token
    Then the response status is 204

  Scenario: Sign-out fails when no token is provided
    When POST /api/v1/auth/sign-out without Authorization header
    Then the response status is 401

  Scenario: Sign-out fails when the token is malformed
    When POST /api/v1/auth/sign-out with Authorization "Bearer not.a.jwt.token"
    Then the response status is 401

  Scenario: Sign-out fails when the token is expired
    When POST /api/v1/auth/sign-out with an expired JWT signed with the correct key
    Then the response status is 401

  Scenario: Sign-out fails when the token is signed with the wrong key
    When POST /api/v1/auth/sign-out with a JWT signed with a different secret key
    Then the response status is 401
