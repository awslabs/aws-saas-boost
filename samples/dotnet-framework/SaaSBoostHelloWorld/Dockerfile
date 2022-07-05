#escape=`

# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License").
# You may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Depending on the operating system of the host machines(s) that will build or run the containers, the image specified in the FROM statement may need to be changed.
# For more information, please see https://aka.ms/containercompat 
#
# This Dockerfile expands on the ideas from the Windows Insider Dockerfile for the LogMonitor project
# (see https://techcommunity.microsoft.com/t5/containers/windows-containers-log-monitor-opensource-release/ba-p/973947)
#
# First, Install IIS and ASP.NET and delete the default IIS web app.
#
# Make a directory to save the LogMonitor files in.
# LogMonitor doesn't like to read files from the root of a drive, so we need a subfolder. Match this to your log file config.
# Make sure your LogMonitorConfig.json file is UTF-8 encoded with no BOM or LogMonitor.exe will fail to start.
#
# A couple of example LogMonitor configurations from Microsoft:
# https://raw.githubusercontent.com/microsoft/iis-docker/master/windowsservercore-insider/LogMonitorConfig.json
# https://raw.githubusercontent.com/microsoft/windows-container-tools/master/LogMonitor/src/LogMonitor/sample-config-files/IIS/LogMonitorConfig.json
#
# Make a directory for Log4Net to write the application log file to. This path needs to match whatever you set in Web.config.
# Our LogMonitor config file also needs to refer to this path so it can relay the log entries to CloudWatch Logs.
#
# Make a directory to use as the mount point for our SMB Mounts. Make sure to enter this as the "mount point" in the SaaS Boost application config.
# SaaS Boost uses SMB Global Mappings to the FSx Windows File Share.
# The FSx drive letter you choose will be mapped to the path entered as "mount point" in the application config.

FROM mcr.microsoft.com/dotnet/framework/runtime:4.8-windowsservercore-ltsc2019

SHELL ["powershell", "-Command", "$ErrorActionPreference = 'Stop'; $ProgressPreference = 'SilentlyContinue';"]

RUN Add-WindowsFeature Web-Server; `
    Add-WindowsFeature NET-Framework-45-ASPNET; `
    Add-WindowsFeature Web-Asp-Net45; `
    C:\Windows\Microsoft.NET\Framework64\v4.0.30319\ngen update; `
    C:\Windows\Microsoft.NET\Framework\v4.0.30319\ngen update; `
    Remove-Item -Recurse C:\inetpub\wwwroot\*; `
    New-Item -ItemType Directory C:\LogMonitor; `
    New-Item -ItemType Directory C:\Logs; `
    New-Item -ItemType Directory C:\Images; `
    $downloads = `
    @( `
        @{ `
            uri = 'https://dotnetbinaries.blob.core.windows.net/servicemonitor/2.0.1.10/ServiceMonitor.exe'; `
            outFile = 'C:\ServiceMonitor.exe' `
        }, `
        @{ `
            uri = 'https://github.com/microsoft/windows-container-tools/releases/download/v1.1/LogMonitor.exe'; `
            outFile = 'C:\LogMonitor\LogMonitor.exe' `
        } `
    ); `
    $downloads.ForEach({ Invoke-WebRequest -UseBasicParsing -Uri $psitem.uri -OutFile $psitem.outFile });

# Install Roslyn compilers
RUN Invoke-WebRequest https://api.nuget.org/packages/microsoft.net.compilers.2.9.0.nupkg -OutFile C:\microsoft.net.compilers.2.9.0.zip; `
    Expand-Archive -Path C:\microsoft.net.compilers.2.9.0.zip -DestinationPath C:\RoslynCompilers; `
    Remove-Item C:\microsoft.net.compilers.2.9.0.zip -Force; `
    C:\Windows\Microsoft.NET\Framework64\v4.0.30319\ngen install C:\RoslynCompilers\tools\csc.exe /ExeConfig:C:\RoslynCompilers\tools\csc.exe | `
    C:\Windows\Microsoft.NET\Framework64\v4.0.30319\ngen install C:\RoslynCompilers\tools\vbc.exe /ExeConfig:C:\RoslynCompilers\tools\vbc.exe | `
    C:\Windows\Microsoft.NET\Framework64\v4.0.30319\ngen install C:\RoslynCompilers\tools\VBCSCompiler.exe /ExeConfig:C:\RoslynCompilers\tools\VBCSCompiler.exe | `
    C:\Windows\Microsoft.NET\Framework\v4.0.30319\ngen install C:\RoslynCompilers\tools\csc.exe /ExeConfig:C:\RoslynCompilers\tools\csc.exe | `
    C:\Windows\Microsoft.NET\Framework\v4.0.30319\ngen install C:\RoslynCompilers\tools\vbc.exe /ExeConfig:C:\RoslynCompilers\tools\vbc.exe | `
    C:\Windows\Microsoft.NET\Framework\v4.0.30319\ngen install C:\RoslynCompilers\tools\VBCSCompiler.exe  /ExeConfig:C:\RoslynCompilers\tools\VBCSCompiler.exe

ENV ROSLYN_COMPILER_LOCATION=C:\RoslynCompilers\tools

# Change the startup type of the IIS service from Automatic to Manual
# Use sc.exe not just sc which PowerShell aliases to Set-Content
RUN sc.exe config w3svc start= demand

# Switch back to CMD.exe to overcome character escape problems
SHELL ["cmd.exe", "/S", "/C"]
# Edit the applicationHost.config file to
# Enable ETW logging for Default Web Site on IIS
# Create a Virtual Directory to serve images from our Docker volume mapped file share
RUN C:\Windows\system32\inetsrv\appcmd.exe set config -section:system.applicationHost/sites /[name="'Default Web Site'"].logFile.logTargetW3C:"File,ETW" /commit:apphost
RUN C:\Windows\system32\inetsrv\appcmd.exe set config -section:system.applicationHost/sites /+"[name='Default Web Site'].[path='/Images']" /commit:apphost
RUN C:\Windows\system32\inetsrv\appcmd.exe set config -section:system.applicationHost/sites /+"[name='Default Web Site'].[path='/Images'].[path='/',physicalPath='C:\Images']" /commit:apphost

EXPOSE 80

# Wrap ServiceMonitor with LogMonitor. Be sure to use ServiceMonitor to start the WWW Service.
# If you start w3svc with net it won't propogate the system environment variables to the IIS process.
ENTRYPOINT ["C:\\LogMonitor\\LogMonitor.exe", "C:\\ServiceMonitor.exe", "w3svc"]

# Visual Studio sets this source argument (the path where the .csproj file is) as part of MSBuild
ARG source
WORKDIR /inetpub/wwwroot
COPY ${source:-obj/Docker/publish} .
COPY LogMonitorConfig.json .

# Use our LogMonitor configuration
RUN move C:\inetpub\wwwroot\LogMonitorConfig.json C:\LogMonitor\
