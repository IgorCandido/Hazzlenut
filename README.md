Work list:

- Implement logging
    - Using log-effect
        Create a layer that transforms the F into a future (cause we are using lightbend products) 
        and allow us to then pipe
        the future to self to allows to continue on result of log (???). Maybe just flatMap on it...
        Note( for comprehension seems to do the trick, need to add tests to make sure that the actor calls indeed the
        function that we are yielding(fetchAccessToken))
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
