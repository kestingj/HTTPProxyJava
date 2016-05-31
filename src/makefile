JFLAGS = -g
JC = javac
.SUFFIXES: .java .class
.java.class:
		$(JC) $(JFLAGS) $*.java

CLASSES = \
		HTTPProxy.java \
		HTTPProxyClient.java \
		INetAddress.java \

default: classes

classes: $(CLASSES:.java=.class)

clean:
		$(RM) *.class