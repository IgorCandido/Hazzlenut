Work list:

- Create dispatcher for oauth client requests
- Implement tests with the TestIO instead of the real service
- Find best way to create/re-use ZIO runtimes

Future list:

- Eventually find a way to do an end to end test automated for the twitch login (oauth)
- Find a way to create a ValidationResult from Bifunctor to work with mapN (from K type to FieldError[K] removing the generic, maybe with a type field?)