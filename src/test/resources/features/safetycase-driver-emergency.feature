Feature: Driver Emergency (US04)

  # US04 S1 – Driver with an active shift creates an emergency
  Scenario: Driver creates a new emergency with coordinates
    Given a driver has an active shift
    When POST /api/v1/driver-emergencies with a valid UUID id, shiftId, activatedAt and coordinates
    Then the response status is 201
    And the response body contains id, status "ACTIVE", priority "CRITICAL", activatedAt, receivedAt

  Scenario: Driver creates a new emergency without coordinates
    Given a driver has an active shift
    When POST /api/v1/driver-emergencies with a valid UUID id, shiftId, activatedAt and no coordinates
    Then the response status is 201
    And the stored emergency has a null location

  # US04 S2 – Offline-queued emergency and idempotent retry
  Scenario: Emergency with an old activatedAt is accepted and stores both timestamps
    Given a driver has an active shift
    When POST /api/v1/driver-emergencies with activatedAt set to an hour in the past
    Then the response status is 201
    And the stored activatedAt differs from receivedAt

  Scenario: Identical retry of an already-stored emergency returns 200 and one row
    Given a driver has already created an emergency with a given id and payload
    When POST /api/v1/driver-emergencies with the same id and the same payload
    Then the response status is 200
    And the response body contains the same id
    And the database still contains exactly one emergency row

  Scenario: Closed shift is still accepted when creating an emergency
    Given a driver's shift is in CLOSED status
    When POST /api/v1/driver-emergencies with that shiftId
    Then the response status is 201

  # US04 S3 – Passenger panic button
  # NOT COVERED: requires passenger groups — will be addressed in US08 and US10 S5

  # US04 S4 – Driver reads their own emergency
  Scenario: Emergency owner retrieves their own emergency
    Given a driver has created an emergency
    When GET /api/v1/driver-emergencies/{id} with the owner's JWT
    Then the response status is 200
    And the response body contains id, status, activatedAt, receivedAt
    And the response body does not contain the outcome field

  Scenario: Another driver cannot read the emergency
    Given a driver has created an emergency
    When GET /api/v1/driver-emergencies/{id} with a different driver's JWT
    Then the response status is 403
    And the response code is "EMERGENCY_ACCESS_DENIED"

  Scenario: Non-existent emergency id returns the same code as another driver's emergency
    When GET /api/v1/driver-emergencies/nonexistent-id with a driver JWT
    Then the response status is 403
    And the response code is "EMERGENCY_ACCESS_DENIED"

  # US04 rejections – POST

  Scenario: Same id with a different payload is rejected
    Given a driver has already created an emergency with a given id
    When POST /api/v1/driver-emergencies with the same id but different activatedAt
    Then the response status is 409
    And the response code is "EMERGENCY_ID_REUSED"
    And the database still contains exactly one emergency row

  Scenario: Same id from another driver is rejected
    Given driver A has created an emergency with a given id
    When driver B POSTs /api/v1/driver-emergencies with the same id on their own shift
    Then the response status is 409
    And the response code is "EMERGENCY_ID_REUSED"

  Scenario: Shift belonging to another driver is rejected with SHIFT_NOT_AUTHORIZED
    Given driver A tries to report on driver B's shift
    When POST /api/v1/driver-emergencies with driver A's JWT and driver B's shiftId
    Then the response status is 403
    And the response code is "SHIFT_NOT_AUTHORIZED"

  Scenario: Non-existent shiftId returns the same body as another driver's shift
    Given a non-existent shiftId and another driver's shiftId
    When POST /api/v1/driver-emergencies for both
    Then both responses are identical 403 bodies

  Scenario: Only latitude provided without longitude returns INCOMPLETE_COORDINATES
    When POST /api/v1/driver-emergencies with latitude but no longitude
    Then the response status is 422
    And the response code is "INCOMPLETE_COORDINATES"

  Scenario: Latitude out of valid range returns INVALID_COORDINATES
    When POST /api/v1/driver-emergencies with latitude 90.1
    Then the response status is 422
    And the response code is "INVALID_COORDINATES"

  Scenario: activatedAt more than 5 minutes in the future is rejected
    When POST /api/v1/driver-emergencies with activatedAt 400 seconds in the future
    Then the response status is 422
    And the response code is "INVALID_ACTIVATION_TIME"

  Scenario: id is not a valid UUID
    When POST /api/v1/driver-emergencies with id "not-a-uuid"
    Then the response status is 422
    And the response code is "VALIDATION_FAILED"

  Scenario: shiftId is missing
    When POST /api/v1/driver-emergencies without shiftId
    Then the response status is 422
    And the response code is "VALIDATION_FAILED"

  Scenario: activatedAt is missing
    When POST /api/v1/driver-emergencies without activatedAt
    Then the response status is 422
    And the response code is "VALIDATION_FAILED"

  Scenario: SUPERVISOR role cannot create an emergency
    When POST /api/v1/driver-emergencies with a SUPERVISOR JWT
    Then the response status is 403

  Scenario: PASSENGER role cannot create an emergency
    When POST /api/v1/driver-emergencies with a PASSENGER JWT
    Then the response status is 403

  Scenario: No token returns 401 on POST
    When POST /api/v1/driver-emergencies without Authorization header
    Then the response status is 401

  # US04 rejections – GET

  Scenario: SUPERVISOR role cannot read an emergency via driver endpoint
    When GET /api/v1/driver-emergencies/{id} with a SUPERVISOR JWT
    Then the response status is 403

  Scenario: PASSENGER role cannot read an emergency via driver endpoint
    When GET /api/v1/driver-emergencies/{id} with a PASSENGER JWT
    Then the response status is 403

  Scenario: No token returns 401 on GET
    When GET /api/v1/driver-emergencies/{id} without Authorization header
    Then the response status is 401

  # Concurrency

  Scenario: Eight identical concurrent creations result in exactly one 201, seven 200, and one DB row
    Given 8 threads each hold the same emergency payload
    When all 8 threads POST /api/v1/driver-emergencies simultaneously
    Then exactly 1 response has status 201
    And exactly 7 responses have status 200
    And the database contains exactly one emergency row
