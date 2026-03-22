# Stack Overflow

## Description & Symptoms

**Symptoms:** `java.lang.StackOverflowError` thrown by the JVM. The affected thread crashes, but the application may continue running. If the error occurs in a critical thread (like a request handler), that specific operation fails while other parts of the application remain functional.

Users experience:
- Immediate thread termination with StackOverflowError
- Request failures if error occurs in web request processing
- Potential application instability if error occurs repeatedly
- Stack traces with hundreds or thousands of identical method calls

## How to Run

```bash
# Default stack size
java -jar application.jar StackOverflow

# Test with different stack sizes
java -Xss256k -jar application.jar StackOverflow   # Smaller stack, crashes sooner
java -Xss4m -jar application.jar StackOverflow     # Larger stack, deeper recursion allowed
java -Xss8m -jar application.jar StackOverflow     # Very large stack
```

This simulation demonstrates:
- Infinite recursion leading to stack overflow
- Different recursion patterns (direct vs indirect)
- Impact of stack size on recursion depth
- Proper vs improper recursion implementations

## How to Detect

### Stack Trace Analysis
```bash
# Stack trace in logs shows very deep call chain (typically 1000+ frames)
Exception in thread "main" java.lang.StackOverflowError
    at com.example.RecursiveMethod.calculate(RecursiveMethod.java:15)
    at com.example.RecursiveMethod.calculate(RecursiveMethod.java:15)
    at com.example.RecursiveMethod.calculate(RecursiveMethod.java:15)
    ... (repeats hundreds of times)
```

### Patterns to Look For
- **Identical method names** repeating throughout the entire stack trace
- **Missing base case** in recursive methods
- **Circular method calls** (A → B → A → B...)
- **Deep object graph traversal** (toString, equals, hashCode on circular references)

### JVM Stack Size Information
```bash
# Check current stack size
java -XX:+PrintFlagsFinal -version | grep ThreadStackSize

# Default is usually 512KB-1MB per thread
```

## Step-by-Step Analysis

### 1. Examine Stack Trace Depth
Count the number of stack frames:
```bash
# If stack trace is in a log file
grep -c "at com.example.Method" stacktrace.log
```

### 2. Identify Recursion Pattern
Look for:
- **Direct recursion**: Same method calling itself
- **Indirect recursion**: Method A → Method B → Method A
- **Object circular references**: toString() → toString() loops

### 3. Check for Base Case
In recursive methods, verify:
- Is there a termination condition?
- Is the condition reachable with given inputs?
- Does the recursive case make progress toward the base case?

### 4. Analyze Input Data Structure
For data structure traversal:
- Are there circular references in the object graph?
- Is the data structure deeper than expected?
- Are there cycles in linked structures (lists, trees, graphs)?

### 5. Test Recursion Depth Limits
```java
public class RecursionTest {
    private static int depth = 0;
    
    public static void testRecursionDepth() {
        depth++;
        System.out.println("Recursion depth: " + depth);
        testRecursionDepth(); // Will eventually overflow
    }
}
```

## Root Cause

Each method call creates a stack frame containing:
- Local variables
- Method parameters  
- Return address
- Partial results

When stack space (controlled by `-Xss`) is exhausted, `StackOverflowError` is thrown.

Common causes:
- **Missing base case** in recursive methods
- **Incorrect base case** that's never reached
- **Circular object references** in toString/equals/hashCode
- **Deeply nested data structures** (XML/JSON parsing)
- **Framework interceptor chains** gone wrong (AOP, filters)

## How to Fix / Prevent

### 1. Fix Missing Base Case
```java
// ❌ BAD: No base case - infinite recursion
public int factorial(int n) {
    return n * factorial(n - 1); // Will never stop!
}

// ✅ GOOD: Proper base case
public int factorial(int n) {
    if (n <= 1) return 1;           // Base case stops recursion
    return n * factorial(n - 1);    // Recursive case makes progress
}
```

### 2. Convert Recursion to Iteration
```java
// Recursive version (can overflow)
public int fibonacciRecursive(int n) {
    if (n <= 1) return n;
    return fibonacciRecursive(n - 1) + fibonacciRecursive(n - 2);
}

// ✅ Iterative version (no stack overflow possible)
public int fibonacciIterative(int n) {
    if (n <= 1) return n;
    
    int a = 0, b = 1;
    for (int i = 2; i <= n; i++) {
        int temp = a + b;
        a = b;
        b = temp;
    }
    return b;
}
```

### 3. Use Tail Recursion (Where Supported)
```java
// ❌ Not tail recursive - stack grows
public int factorial(int n) {
    if (n <= 1) return 1;
    return n * factorial(n - 1); // Multiplication happens after recursive call
}

// ✅ Tail recursive with accumulator
public int factorial(int n) {
    return factorialTail(n, 1);
}

private int factorialTail(int n, int accumulator) {
    if (n <= 1) return accumulator;
    return factorialTail(n - 1, n * accumulator); // No operation after recursive call
}
```

### 4. Guard Against Deep Recursion
```java
public class SafeRecursion {
    private static final int MAX_DEPTH = 1000;
    
    public int safeCalculate(int n, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("Recursion too deep: " + depth);
        }
        
        if (n <= 1) return 1;
        return n * safeCalculate(n - 1, depth + 1);
    }
    
    public int calculate(int n) {
        return safeCalculate(n, 0);
    }
}
```

### 5. Fix Circular References in Objects
```java
// ❌ BAD: Can cause stack overflow in toString()
public class Person {
    private String name;
    private Person friend; // Circular reference possible
    
    @Override
    public String toString() {
        return "Person{name='" + name + "', friend=" + friend + "}";
        // If friend points back to this person → infinite recursion
    }
}

// ✅ GOOD: Safe toString() implementation
public class Person {
    private String name;
    private Person friend;
    
    @Override
    public String toString() {
        return "Person{name='" + name + "', friend=" + 
               (friend != null ? friend.getName() : "null") + "}";
    }
    
    // Or use a Set to track visited objects
    public String toStringDeep() {
        return toStringDeep(new HashSet<>());
    }
    
    private String toStringDeep(Set<Person> visited) {
        if (visited.contains(this)) {
            return "Person{name='" + name + "', friend=<circular>}";
        }
        
        visited.add(this);
        return "Person{name='" + name + "', friend=" + 
               (friend != null ? friend.toStringDeep(visited) : "null") + "}";
    }
}
```

### 6. Use Trampoline Pattern for Functional Recursion
```java
// Trampoline to avoid stack overflow in functional-style recursion
public abstract class Trampoline<T> {
    public abstract boolean isComplete();
    public abstract T result();
    public abstract Trampoline<T> compute();
    
    public static <T> Trampoline<T> done(final T result) {
        return new Trampoline<T>() {
            public boolean isComplete() { return true; }
            public T result() { return result; }
            public Trampoline<T> compute() { throw new UnsupportedOperationException(); }
        };
    }
    
    public static <T> Trampoline<T> call(final Supplier<Trampoline<T>> computation) {
        return new Trampoline<T>() {
            public boolean isComplete() { return false; }
            public T result() { throw new UnsupportedOperationException(); }
            public Trampoline<T> compute() { return computation.get(); }
        };
    }
    
    public T execute() {
        Trampoline<T> current = this;
        while (!current.isComplete()) {
            current = current.compute();
        }
        return current.result();
    }
}

// Usage example
public Trampoline<Integer> factorialTrampoline(int n, int acc) {
    if (n <= 1) {
        return Trampoline.done(acc);
    }
    return Trampoline.call(() -> factorialTrampoline(n - 1, n * acc));
}
```

### 7. Increase Stack Size (Temporary Solution)
```bash
# Increase per-thread stack size
java -Xss2m MyApplication    # 2MB per thread stack
java -Xss4m MyApplication    # 4MB per thread stack

# Note: This doesn't fix the underlying issue, just delays the error
```

## Prevention Strategies

- **Code review**: Every recursive method must have a clear base case
- **Unit testing**: Test recursive methods with boundary values (0, 1, large numbers)
- **Static analysis**: Tools like SpotBugs can detect some infinite recursion patterns
- **Defensive programming**: Add depth guards to recursive methods
- **Prefer iteration**: Use loops instead of recursion when possible
- **Be careful with toString/equals/hashCode**: Avoid traversing object graphs that might have cycles

## Key Takeaways

- Every recursive method MUST have a reachable base case
- Test recursive methods with edge cases and large inputs
- Prefer iterative solutions when recursion depth could be large
- Use depth guards in recursive methods that process external data
- Be extremely careful with toString(), equals(), and hashCode() on objects with references
- Stack overflow is a design issue, not a memory issue - increasing `-Xss` is rarely the right fix
- Consider using tail recursion or trampoline patterns for deep functional recursion
- Always validate that recursive cases make progress toward the base case