Work list:

- Look into getting a token expired error from twitch and asking for renew of token
    - Test what happens with a 4xx and 5xx on the TwitchClient getting the HttpResponse
        - Handle HttpConnectionError with ConnectionError(throwable), 
            create the Hazzlenut error for and handle it with the monadError
            create unmarshaller for this type: {
                                                 "message":"Invalid Token",
                                                 "status":401,
                                                 "error":"Unauthorized"
                                               }
            use above unmarshaller to get the error message and return it as a string
            add this to the httpClient call (maybe making implicit with the twitchError type binding on the request and
            into the implicit)

- Implement twitch get followers (for now polling)
    - Implemented with actor
    - Asks UserInfo
    - Asks Token
    - When Token can't be renewed should die and be resurected when token starts again
- Understand if we need to drop user info when the we get new OAuthToken (not refresh but re-authenticate)
- Write the flow for get followers on a cycle and then publish those to whoever is interested (Events and likely actor on a schedule)
- Refactor twitch get followers (web sockets)
- Write interested party that read follow events and pumps them into client (for now dummy showing on a webpage)
- Write interested party that read follow events and pumps them into client (eventually websockets to a web page)


Future list:

- Same for hosts as followers
- Same for bits as followers
- Same for raids as followers
- Same for subscribers as followers
- Code coverage
- Create dispatcher for oauth client requests
- Find out if we are using ExecutionContext from ZIO/Akka Http on http requests
- Webpage that renders the notifications (scala js reading the webhooks)
- Find a way to create a ValidationResult from Bifunctor to work with mapN (from K type to FieldError[K] removing the generic, maybe with a type field?)
- Eventually find a way to do an end to end test automated for the twitch login (oauth)
