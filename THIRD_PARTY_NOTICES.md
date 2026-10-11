# Third-party components

Commandant remains licensed under the repository LICENSE. Its deployable JAR embeds:

- Klib 0.18.0: Apache License 2.0; https://github.com/kzheart/klib
- Klib's bundled Kether parsing core and dependencies: see Klib's THIRD_PARTY_NOTICES.md
- SnakeYAML, Apache Commons JEXL and Apache Commons Logging: their upstream licenses and notices are retained by the bundle
- OpenJDK Nashorn 15.6: GPL version 2 with the Classpath Exception; https://github.com/openjdk/nashorn
- ASM 7.3.1 modules: BSD license; https://asm.ow2.io/license.html

The Java 11+ Nashorn/ASM runtime is stored as unchanged nested JAR files under META-INF/commandant-js.
Their original license entries remain inside those JARs. Java 8 uses the JDK-provided Nashorn engine instead.
No JavaScript engine or TabooLib runtime is downloaded when the plugin starts.
The Klib OpenContainer compatibility facade is not the TabooLib runtime.
