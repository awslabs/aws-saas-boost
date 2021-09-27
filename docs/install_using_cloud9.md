# Instructions for installing AWS SaaS Boost using AWS Cloud9.

By default, Cloud9 inherits the AWS permissions for the user or role which you are logged into the AWS Console as. Make sure that role has full admin permissions necessary to install AWS SaaS Boost.

1.	Login to AWS Console and create an AWS Cloud9 environment using Amazon Linux 2 and instance type with at least 4GB RAM. For example, choose Other instance type and then select T2 or T3 medium.

2.	Launch the Cloud9 environment by clicking the Open IDE button.

3.	Cloud9 environments have 10GB of disk space by default. Resize the Cloud9 environment (recommend at least 20GB) using the resize.sh script here - https://docs.aws.amazon.com/cloud9/latest/user-guide/move-environment.html#move-environment-resize 
```cd ~
chmod +x ./resize.sh
./resize.sh 20
df -h
```

4.	Run the following command to clone the SaaS Boost git repository:
```cd ~/environment
git clone https://github.com/awslabs/aws-saas-boost.git aws-saas-boost
```
5.	We need a newer version of Apache Maven than the default. To install a more recent version, run the following commands:
```
sudo wget http://repos.fedorapeople.org/repos/dchen/apache-maven/epel-apache-maven.repo -O /etc/yum.repos.d/epel-apache-maven.repo
sudo sed -i s/\$releasever/6/g /etc/yum.repos.d/epel-apache-maven.repo
sudo yum install -y apache-maven
```

6.	Set default Java version back to Amazon Corretto 11 (select option 1 for both)
```
sudo alternatives --config java

sudo alternatives --config javac
```
7.	Upgrade to node 14
```
nvm install 14
```

8.	Install yarn
```
npm install --global yarn
```

9.	Run AWS SaaS Boost install script
```
cd ~/environment/aws-saas-boost
./install.sh
```

10. Follow the install script prompts to install AWS SaaS Boost!
