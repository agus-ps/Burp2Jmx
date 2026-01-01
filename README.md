# Burp2Jmx Converter

**Burp2Jmx** is a Java-based utility designed to automate the conversion of HTTP requests captured in Burp Suite into Apache JMeter test plans (`.jmx`). 

It parses Burp Suite XML dumps and injects the requests as `HTTPSamplerProxy` elements into a specified Thread Group within an existing JMeter template.

## 🚀 Features

* **Burp Suite Integration:** Parses standard XML exports from Burp Suite.
* **JMeter Automation:** Modifies existing `.jmx` files to append new test controllers.
* **Intelligent Parsing:** Automatically extracts:
    * HTTP Methods (GET, POST, PUT, etc.)
    * URLs (Protocol, Domain, Path)
    * Request Bodies (decodes Base64 data from Burp)
* **Controller Organization:** Groups imported requests under a labeled `GenericController`.

## 📋 Prerequisites

* **Java Development Kit (JDK):** Version 8 or higher.
* **Apache JMeter:** To open and run the generated output.
* **Burp Suite:** To capture and export the traffic.

## 🛠️ Installation & Compilation

Since this is a standalone Java file with no external Maven/Gradle dependencies (it uses standard Java XML libraries), you can compile it directly using `javac`.

1. **Clone or download the repository.**
2. **Navigate to the source directory:**
```bash
cd path/to/source

```

3. **Compile the code:**
```bash
javac -d . Burp2Jmx.java

```



## 📖 Usage

### 1. Export Requests from Burp Suite

1. Select the requests you want to convert in the **Proxy History** or **Repeater** tab.
2. Right-click and select **Save items**.
3. Ensure the format is set to **XML**.
4. Save the file (e.g., `dump.xml`).

### 2. Prepare the JMeter Template

Create a basic JMeter Test Plan (`template.jmx`) that contains at least one **Thread Group**. The tool looks for a `ThreadGroup` element to inject the new requests.

### 3. Run the Tool

Once compiled and configured, run the class:

```bash
java com.Burp2Jmx.Burp2Jmx <input_jmx> <burp_dump> <controller_name> <output_jmx>

```

1. **Input JMX:** Path to your base JMeter template.
2. **Burp Dump:** Path to the XML file exported from Burp Suite.
3. **Controller Name:** Name for the transaction controller that will group the requests.
4. **Output JMX:** Path where the new JMeter file will be saved.


*Success Output:*

> Successfully created new JMX file with X requests in controller 'YourControllerName'

## 🧩 Project Structure

```text
src/
└── com/
    └── Burp2Jmx/
        └── Burp2Jmx.java  # Main application logic

```

## ⚠️ Limitations

* **Headers:** Currently, the tool extracts the body and URL, but specific HTTP headers (like Authorization or Content-Type) might need manual review in JMeter depending on the complexity of the request.
