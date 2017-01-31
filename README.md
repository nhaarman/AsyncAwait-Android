# AsyncAwait-Android
[ ![Download](https://maven-badges.herokuapp.com/maven-central/com.nhaarman/asyncawait-android/badge.svg) ](https://maven-badges.herokuapp.com/maven-central/com.nhaarman/asyncawait-android)

**Warning!**
This library was created for Kotlin 1.1-M03. Since M04, the API has changed. See [Kotlin-Coroutines](https://github.com/Kotlin/kotlin-coroutines) for more info.

A library that provides the async/await concept for Android using the coroutines feature in Kotlin 1.1.

## Install

AsyncAwait-Android is available on Maven Central.
For Gradle users, add the following to your `build.gradle`, replacing `x.x.x` with the latest version:

```groovy
repositories {
    mavenCentral()
}
dependencies {
    compile "com.nhaarman:asyncawait-android:x.x.x"
}
```

AsyncAwait-Android uses the 1.1-M03 eap of Kotlin. See the [How to try it](https://blog.jetbrains.com/kotlin/2016/07/first-glimpse-of-kotlin-1-1-coroutines-type-aliases-and-more/) section for details.

## Example

A typical scenario is provided below:

```kotlin

fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)

  asyncUI {
    val textView = TextView(this@MainActivity)
    setContentView(textView)
    val githubUserName = await(retrieveGithubUser()).name
    textView.text = githubUserName
  }
}

fun retrieveGithubUser() = async<User> {
  ...
}

```

In this snippet the coroutine is suspended at `await(retrieveGithubUser())`, and
resumed when the task returned by `retrieveGithubUser()` finishes.
The coroutine is resumed on the main thread, as indicated by `asyncUI`.


## How it works
_Inspired by http://blog.stephencleary.com/2012/02/async-and-await.html._

An asynchronous method looks something like this:

```kotlin
fun foo() = async<String> {
  await(delay(1, SECONDS))
  "bar"
}
```

The call to `async` provides access to the `await` function, and changes the way the method is executed.
It does _not_ move the method call on another thread.
The method is executed synchronously until the first call to `await`, which _may_ result in an asynchronous call.

If the parameter provided to `await` has already completed, the method continuous running synchronously.
If the parameter has not yet completed, `await` tells the awaitable to run the rest of the method when it completes, and then returns from the async method.

Later on, when the awaitable completes, it will execute the remainder of the async method.
The thread on which the remainder is executed is determined by whether the async method was called by `async` or `asyncUI`

## Async

There are two flavors of `async`: one that takes in a parameter (e.g. `async<String>`), and one that does not.
The type passed to `async` determines the result type of the coroutine.

When using `async`, the coroutine will continue on the thread the awaitable ended on.
For example, if you call `await` with a `Task` that is executed on some io thread, the remainder of the coroutine will continue on that io thread:

```
fun foo() = async {
  // Runs on calling thread
  await(someIoTask()) // someIoTask() runs on an io thread
  // Continues on the io thread
}
```

## AsyncUI

If you want to modify UI elements, or do other things that require you to be on the main thread, you can use `asyncUI`.
In contrast to `async`, `asyncUI` _does_ continue on the main thread after a call to `await`:

```
// Runs on main thread
await(someIoTask()) // someIoTask() runs on an io thread
// Continues on the main thread
```

## Await

Inside a coroutine, you can await on a `Task` or pass a lambda.
Since `async` returns a `Task`, you can await on asynchronous methods.
The return type of `await` is either the type parameter passed to `Task` or the return type of the lambda.

When awaiting on a `Task<T>`, there is no thread switching, unless specified explicitly by that task.
When awaiting on a lambda, the lambda _will_ be executed on another thread:

```kotlin
fun foo() = asyncUI {
  // Main thread
  var result = await(bar()) // bar() is executed on the main thread
  // Main thread
  result = await { // Lambda is executed on another thread
    "Bar"
  }
  // Main thread
}

fun bar() = async<String> {
  "Bar"
}
```
