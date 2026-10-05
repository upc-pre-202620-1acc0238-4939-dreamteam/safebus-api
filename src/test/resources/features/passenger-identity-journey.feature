Feature: Passenger Identity and Journey (US23, US06)

  # US23 S1 – Passenger registers successfully
  Scenario: Passenger registers with a valid JPEG face photo
    Given a new user with loginId, password, 8-digit DNI, current termsVersion, and a valid JPEG face photo
    When POST /api/v1/passengers with those fields as multipart/form-data
    Then the response status is 201
    And the response body contains id
    And the stored UserAccount has role PASSENGER and no companyId
    And the stored password hash starts with "$2" (BCrypt)

  Scenario: Passenger registers with a valid PNG face photo
    Given a new user with loginId, password, 8-digit DNI, current termsVersion, and a valid PNG face photo
    When POST /api/v1/passengers with those fields as multipart/form-data
    Then the response status is 201
    And the response body contains id

  # US23 S2 – Validation rejections
  Scenario: Registration fails when DNI has only 7 digits
    Given a request with a 7-digit DNI
    When POST /api/v1/passengers
    Then the response status is 422
    And the response body contains code "INVALID_DNI"

  Scenario: Registration fails when password is omitted
    Given a request with a valid DNI, valid terms, valid photo, but no password
    When POST /api/v1/passengers
    Then the response status is 422
    And the response body contains code "PASSWORD_REQUIRED"

  Scenario: Registration fails when termsVersion is absent
    Given a request with valid fields but no termsVersion
    When POST /api/v1/passengers
    Then the response status is 422
    And the response body contains code "TERMS_NOT_ACCEPTED"

  Scenario: Registration fails when termsVersion does not match the current version
    Given a request with an outdated termsVersion
    When POST /api/v1/passengers
    Then the response status is 422
    And the response body contains code "TERMS_VERSION_INVALID"

  Scenario: Registration fails when no face photo is provided
    Given a request with valid fields but no facePhoto part
    When POST /api/v1/passengers
    Then the response status is 422
    And the response body contains code "FACE_PHOTO_REQUIRED"

  Scenario: Registration fails when face photo is a GIF
    Given a request with a GIF file as facePhoto
    When POST /api/v1/passengers
    Then the response status is 422
    And the response body contains code "FACE_PHOTO_INVALID"

  Scenario: Rollback on save failure leaves no UserAccount and no StoredImage
    Given the PassengerAccount repository is stubbed to throw on save
    When POST /api/v1/passengers with valid fields
    Then the transaction rolls back
    And no UserAccount with that loginId exists
    And no StoredImage was persisted

  # US23 S3 – Duplicate DNI
  Scenario: Second registration with the same DNI (with spaces) returns 409
    Given a passenger account already exists with DNI "99887766"
    When POST /api/v1/passengers with DNI "  99887766  " (same DNI surrounded by spaces)
    Then the response status is 409
    And the response body contains code "DNI_ALREADY_REGISTERED"
    And the first account's password hash is unchanged
    And no UserAccount was created for the second loginId

  # US06 S1 – Passenger starts a journey
  Scenario: Passenger with valid QR starts a new journey
    Given a bus with QR code "JCT-QR-001" has an active shift
    And the passenger has no existing active journey
    When POST /api/v1/journeys with busQrCode "JCT-QR-001" and PASSENGER JWT
    Then the response status is 201
    And the response body contains id

  Scenario: Passenger scans the same bus QR a second time returns 200 with same journey id
    Given a passenger already has an active journey on bus with QR "JCT-QR-001"
    When POST /api/v1/journeys with busQrCode "JCT-QR-001" and PASSENGER JWT
    Then the response status is 200
    And the response body contains the same journey id

  # US06 S2 – Rejection cases
  Scenario: Unknown QR code returns 422 BUS_QR_INVALID
    When POST /api/v1/journeys with an unknown busQrCode
    Then the response status is 422
    And the response body contains code "BUS_QR_INVALID"

  Scenario: Passenger already on a different bus returns 409 ACTIVE_JOURNEY_EXISTS
    Given a passenger has an active journey on bus1
    When POST /api/v1/journeys with the QR code of bus2
    Then the response status is 409
    And the response body contains code "ACTIVE_JOURNEY_EXISTS"

  Scenario: No JWT token returns 401
    When POST /api/v1/journeys with no Authorization header
    Then the response status is 401

  Scenario: Non-PASSENGER role returns 403
    When POST /api/v1/journeys with a SUPERVISOR JWT
    Then the response status is 403

  # US06 S4 – Passenger ends a journey
  Scenario: Passenger ends their active journey with reason MANUAL
    Given a passenger has an active journey
    When POST /api/v1/journeys/{id}/end with reason "MANUAL"
    Then the response status is 200
    And changed is true

  Scenario: Ending an already-ended journey is idempotent and returns changed false
    Given a passenger's journey is already ENDED
    When POST /api/v1/journeys/{id}/end with reason "SIGN_OUT"
    Then the response status is 200
    And changed is false

  Scenario: Another passenger attempting to end the journey returns 403
    Given a journey owned by passenger A
    When POST /api/v1/journeys/{id}/end with a JWT for passenger B
    Then the response status is 403
    And the response body contains code "JOURNEY_ACCESS_DENIED"

  Scenario: Invalid end reason returns 422 INVALID_END_REASON
    Given a passenger has an active journey
    When POST /api/v1/journeys/{id}/end with reason "AUTOMATIC_SEPARATION"
    Then the response status is 422
    And the response body contains code "INVALID_END_REASON"
