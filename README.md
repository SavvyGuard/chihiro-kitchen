## README

### Running the Code

If you have Intellij Ultimate, you can install the scala plugin
and be able to run this out of the box by clicking the 
button next to the ChihiroKitchen App object.

Otherwise, in the command line you can install sbt and
execute

```$xslt
sbt run
```

### Configurations

You can change the configuration settings in 
`src/main/resource/application.conf`.

### Overflow Logic

When a shelf is full and there is a new order that
needs to be placed, that new order automatically goes
on the overflow shelf. I did not choose to have another
 item first get transferred to the overflow shelf because
 in terms of business logic it doesn't make much sense. 
 
In a kitchen (and general operations) work, the primary requirement
 is ease of the most common case. The last thing a kitchen worker
 wants to do when placing orders is to first figure out which of
 the previous orders should be first shuffled before placing
 the new order. This messes up operational flow.
 
If both the desired shelf and the overflow shelf is full, we then
initiate a shuffle process where items on the overflow shelf get
transferred to open shelf spaces, hopefully making room in
the overflow shelf. This is an acceptable disruption of operations
because the alternative is to actually discard an order, which
has much worse customer impact.

Lastly, if an order must be discarded no matter what to make room
then the order then we discard the item with the least amount shelf
life value. The idea here is to minimize customer dissatisfaction.
The delta in dissatisfaction a customer might have between receiving
a stale order and not receiving an order at all is less than that
of a customer receiving a fresh order.

### Why Akka

I wanted to handle concurrency without having to worry about managing
individual threads or locking objects for concurrent access. The actor
model handles all of that for you by encapsulating all interactions
as message passing.

### Reliability and Resilience

One thing to note is that Akka does not guarantee that all messages
will be delivered. Instead, it provides `at-most-once` delivery
guarantees. In practice though, akka is fairly consistently `exactly-once`
delivery. It begins to have issues when scaling out to a multi-instance
akka cluster. However, message drops should still be relatively rare
edge case, and I didn't try to handle it here as the two generals
problem doesn't really have a concrete solution, and the degree
of reliability within akka on a single host is high enough that this
shouldn't really be a worry.

Another thing that Akka gives you for free is the resilience of your
actors. Any kind of failures in one actor should be limited purely
to that actor (and its children). Thus, in this demo's design
where each order has its own `assistant`, this means that issues
with one order will not have any impact on other orders.

### Design Setup of Each Actor

Each actor handles messages using a functional approach as mentioned
above. However, I prefer to separate out the business logic from the
actor message handling structure itself. In my experience, business
logic can quickly get very complex and including it within the
actor structure itself can lead to significant difficulties testing
the code.

However, I took a bit of a shortcut here since a lot of the actors
have minimal logic and only fully extracted out complex logic into
separate classes (like the storage room itself).

### Some Further Thoughts

I use ZonedDatetime directly out of convenience and to save time.
However, in a production system I would recommend using `Clock` via
dependency inject instead in order to allow for tests to control the
flow of time easier.

A few places I didn't use the optimal computational complexity. In
situations where n is fairly small (true for almost anything
with physical restrictions like an actual facility) choosing an
algorithm that is O(n) vs O(1) has no perceptible difference. Thus,
I'll rather write my code in such a way to make readability easier 
without introducing extra objects like a minheap to save a few dozen
extra calculations.
