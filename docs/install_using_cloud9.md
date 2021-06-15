# Instructions for installing AWS SaaS Boost using AWS Cloud9.

Cloud9 use the permissions for the user or role which you are logged into the AWS Console. Make sure that role has full admin permissions necessary to install AWS SaaS Boost.

1.	Login to AWS Console and create an AWS Cloud9 environment using instance type with at least 4GB RAM.

2.	Log onto the Cloud9 environment

3.	Resize the Cloud9 environment (recommend at least 20GB) using the resize.sh script here - https://docs.aws.amazon.com/cloud9/latest/user-guide/move-environment.html#move-environment-resize 
```cd ~
chmod +x ./resize.sh
./resize.sh 20
df -h
```

4.	Run the following commands to clone the SaaS Boost git repository:
```cd ~/environment
git clone https://github.com/awslabs/aws-saas-boost.git aws-saas-boost
```
5.	Install Apache Maven
```
sudo yum install -y maven
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
