Work list:

- Create dispatcher for oauth client requests
- Find out if we are using ExecutionContext from ZIO/Akka Http on http requests
- Create abstraction for OAuth client? Need to find a way to enable tests of asynchronous and error handling between ZIO and future without calling the real oauth client or instead enrich the type class and test that logic (difficult cause of ZIO)

Future list:

- Eventually find a way to do an end to end test automated for the twitch login (oauth)
- Find a way to create a ValidationResult from Bifunctor to work with mapN (from K type to FieldError[K] removing the generic, maybe with a type field?)
- Code coverage