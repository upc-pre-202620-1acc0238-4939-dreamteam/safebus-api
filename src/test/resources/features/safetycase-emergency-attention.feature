Feature: Emergency Attention (US10)

  # US10 S1 – Supervisor starts attention on a driver emergency
  Scenario: Supervisor of the same company starts attention
    Given an ACTIVE emergency belonging to company C
    When POST /api/v1/emergencies/{id}/start-attention with a supervisor JWT for company C
    Then the response status is 200
    And the response body contains id, status "IN_PROGRESS", responsibleSupervisorId, attentionStartedAt
    And the stored emergency has status IN_PROGRESS

  Scenario: Starting attention twice returns INVALID_TRANSITION
    Given an emergency is already IN_PROGRESS
    When POST /api/v1/emergencies/{id}/start-attention again with the same supervisor
    Then the response status is 409
    And the response code is "INVALID_TRANSITION"
    And the stored status and responsibleSupervisorId are unchanged

  Scenario: Supervisor from another company is denied
    Given an ACTIVE emergency belonging to company C
    When POST /api/v1/emergencies/{id}/start-attention with a supervisor JWT for a different company
    Then the response status is 403
    And the response code is "EMERGENCY_ACCESS_DENIED"
    And the stored emergency still has status ACTIVE

  Scenario: Non-existent emergency id returns the same code as another company's emergency
    When POST /api/v1/emergencies/nonexistent-id/start-attention with a supervisor JWT
    Then the response status is 403
    And the response code is "EMERGENCY_ACCESS_DENIED"

  Scenario: DRIVER role cannot start attention
    When POST /api/v1/emergencies/{id}/start-attention with a DRIVER JWT
    Then the response status is 403

  Scenario: PASSENGER role cannot start attention
    When POST /api/v1/emergencies/{id}/start-attention with a PASSENGER JWT
    Then the response status is 403

  Scenario: No token returns 401 on start-attention
    When POST /api/v1/emergencies/{id}/start-attention without Authorization header
    Then the response status is 401

  # US10 S4 – Supervisor closes the emergency
  Scenario: Valid lifecycle — start attention then close with an outcome
    Given an ACTIVE emergency
    When POST /api/v1/emergencies/{id}/start-attention succeeds
    And POST /api/v1/emergencies/{id}/close with outcome "all clear"
    Then the close response status is 200
    And the close response body contains id, status "CLOSED", closedAt, outcome "all clear"
    And the stored emergency has status CLOSED

  Scenario: Close from ACTIVE status without starting attention first
    Given an ACTIVE emergency
    When POST /api/v1/emergencies/{id}/close with outcome "outcome"
    Then the response status is 422
    And the response code is "ATTENTION_NOT_STARTED"
    And the stored emergency still has status ACTIVE

  Scenario: Close with null outcome returns OUTCOME_REQUIRED
    Given an IN_PROGRESS emergency
    When POST /api/v1/emergencies/{id}/close with outcome null
    Then the response status is 422
    And the response code is "OUTCOME_REQUIRED"
    And the stored emergency still has status IN_PROGRESS

  Scenario: Close with blank outcome returns OUTCOME_REQUIRED
    Given an IN_PROGRESS emergency
    When POST /api/v1/emergencies/{id}/close with outcome ""
    Then the response status is 422
    And the response code is "OUTCOME_REQUIRED"

  Scenario: Close with outcome longer than 500 characters returns OUTCOME_TOO_LONG
    Given an IN_PROGRESS emergency
    When POST /api/v1/emergencies/{id}/close with an outcome of 501 characters
    Then the response status is 422
    And the response code is "OUTCOME_TOO_LONG"
    And the stored emergency still has status IN_PROGRESS

  Scenario: Closing an already-CLOSED emergency returns INVALID_TRANSITION and preserves first outcome
    Given a CLOSED emergency with outcome "first outcome"
    When POST /api/v1/emergencies/{id}/close with outcome "second"
    Then the response status is 409
    And the response code is "INVALID_TRANSITION"
    And the stored outcome is still "first outcome"

  Scenario: Supervisor from another company cannot close the emergency
    Given an IN_PROGRESS emergency belonging to company C
    When POST /api/v1/emergencies/{id}/close with a supervisor JWT for a different company
    Then the response status is 403
    And the response code is "EMERGENCY_ACCESS_DENIED"
    And the stored emergency still has status IN_PROGRESS

  Scenario: DRIVER role cannot close an emergency
    When POST /api/v1/emergencies/{id}/close with a DRIVER JWT
    Then the response status is 403

  Scenario: No token returns 401 on close
    When POST /api/v1/emergencies/{id}/close without Authorization header
    Then the response status is 401

  # Concurrency

  Scenario: Two supervisors start attention simultaneously — exactly one succeeds, one gets INVALID_TRANSITION
    Given an ACTIVE emergency
    When 2 supervisor threads POST /api/v1/emergencies/{id}/start-attention simultaneously
    Then exactly 1 response has status 200
    And exactly 1 response has status 409
