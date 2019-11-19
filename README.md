Work list:
- Clean and reorganize the services.twitch namespace
- Think of a way to enforce cursor and total on sequence requests (TwitchClient)
- Understand why the TwitchReply RootJsonFormat isn't being correctly imported implicitly 
(without importing in the context of the method it says that if finds an ambiguous method to apply)
    - Implement tests to assure that the serialization and deserializion of the hand rolled json formating works
    (TwitchReply[User])
- Implement logging
    - Using log-effect
        Add more tests
        Make sure to whilst waiting for the Logger result the actor is already only waiting to 
        receive the outcome of the logger to run the become function
- Implement twitch get followers (for now polling)
    - Implemented with actor (Almost)
    - Asks UserInfo (Done, not tested)
    - Asks Token (Done, not tested)
    - Returning FollowersReply, implement lense for Users following
    - Implement a test/http action that allows to see our followers as manual test
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
