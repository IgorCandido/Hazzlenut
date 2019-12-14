Work list:
- Make the TokenGuardian a supervisor aware of failures and allow for service actors to rejoin and recreate fallen services
- Need to understand how to use BackOffSupervisor
- Write tests to make sure that persisted state of the Followers actor is properly managed
    - Guarantee that Persistent actor restarted on a failure case is automatically started
    - When Persistent actor is properly killed (terminated) do not do an auto start (require the application start message)
- Write the flow for get followers on a cycle and then publish those to whoever is interested (Events and likely actor on a schedule)
- Update the Followers tests to use the stream with the followers instead of mutable state on the twitch client
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
