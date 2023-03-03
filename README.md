### Usage

![Alt Text](./usage.gif)

### Install
```
git clone git@github.com:abugov/ziodiff.git
cd ziodiff
sbt assembly
sudo cp target/scala-2.13/ziodiff.jar /usr/local/bin/
alias ziodiff='pbpaste | java -jar /usr/local/bin/ziodiff.jar'
```