# 亚马逊云科技 SaaS Boost 入门指南

## 内容
[简介](#简介)\
[目标体验](#目标体验)\
[步骤 1 - 设置工具](#步骤-1---设置工具)\
[步骤 2 - 克隆 SaaS Boost 存储库](#步骤-2---克隆-saas-boost-存储库)\
[步骤 3 - 将 SaaS Boost 配置到您的亚马逊云科技账户中](#步骤-3---将-saas-boost-配置到您的亚马逊云科技账户中)\
[步骤 4 - 登录 SaaS Boost](#步骤-4---登录-saas-boost)\
[步骤 5 - 配置层级和应用程序设置](#步骤-5---配置层级和应用程序设置)\
[步骤 6 - 上传您的应用程序服务](#步骤-6---上传您的应用程序服务)\
[步骤 7 -（可选）部署示例应用](#步骤-7---可选部署示例应用)\
[将您的应用程序迁移到 SaaS Boost](#将您的应用程序迁移到-saas-boost)

## 简介
本文档介绍了在亚马逊云科技 SaaS Boost 中安装、配置和运行工作负载的基本步骤。请参阅其他 SaaS Boost 文档，以更全面地了解系统。

虽然本文档概述了设置亚马逊云科技 SaaS Boost 的步骤，但并不深入探讨用户体验或底层技术。相关详细信息包含在 [用户指南](user-guide.md) 和 [开发人员指南](developer-guide.md)中。

## 目标体验
在深入研究设置亚马逊云科技 SaaS Boost 所需的步骤之前，让我们先了解一下环境的基本元素，以便更好地了解 SaaS Boost 支持的功能。下图按设置流程的顺序显示了 SaaS Boost 的关键组件。

![设置流程](images/gs-install-flow.png?raw=true "设置流程")

以下是每个步骤的细分：
1. 为安装过程设置所需的工具
2. 克隆 SaaS Boost 存储库
3. 将 SaaS Boost 功能安装到您的亚马逊云科技账户中
4. 访问 SaaS Boost 管理控制台
5. 根据您的要求配置分层和应用程序
6. 为应用程序中的每个服务上传 Docker 镜像

在此阶段，环境的所有组成部分现在都已准备就绪，可以开始载入租户。您可以使用 SaaS Boost 应用程序载入新租户，也可以从应用程序进行 API 调用以触发新租户的载入。

要考虑的最后一点是应用程序的更新过程。当您的应用程序发生变更时，您可以将最新版本上传到 SaaS Boost。每当上传新版本时，它将自动部署到系统中的所有租户。SaaS Boost 现已准备好作为 SaaS 业务运营，这包括内置于 SaaS Boost 环境中的操作和管理工具。

至此您已经对 SaaS Boost 技术有了一定的了解，接下来让我们来看看设置 SaaS Boost 所涉及的详细信息。

## 步骤 1 - 设置工具
SaaS Boost 在安装过程中使用了一些技术。为操作系统安装和配置以下每个先决条件（如果尚未安装）：
- Java 11 [Amazon Corretto 11](https://docs.aws.amazon.com/corretto/latest/corretto-11-ug/downloads-list.html)
- [Apache Maven](https://maven.apache.org/download.cgi) (see [Installation Instructions](https://maven.apache.org/install.html))
- [AWS Command Line Interface version 2](https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html)
- [Git](https://git-scm.com/downloads)
- [Node 14 (LTS)](https://nodejs.org/download/release/latest-v14.x/)
- [Yarn](https://classic.yarnpkg.com/en/docs/install)

如果您无法或不希望在本地计算机上安装所有必备组件，则可以使用亚马逊云科技 Cloud9 实例来安装 SaaS Boost。按照 [使用 AWS Cloud9 安装](./install-using-cloud9.md)中的步骤操作，然后继续执行 [步骤 3](#步骤-3---将-saas-boost-配置到您的亚马逊云科技账户中)。

## 步骤 2 - 克隆 SaaS Boost 存储库
工具准备就绪后，您现在可以下载 SaaS Boost 的代码和安装脚本。打开终端窗口并导航到要存储 SaaS Boost 资源的目录。使用以下命令以克隆 SaaS Boost 存储库：

`git clone https://github.com/awslabs/aws-saas-boost ./aws-saas-boost`

## 步骤 3 - 将 SaaS Boost 配置到您的亚马逊云科技账户中
现在您已拥有代码，需要在您拥有和管理的亚马逊云科技账户中安装 SaaS Boost。安装过程将执行脚本，这些脚本预置和配置设置 SaaS Boost 所需的所有资源。运行安装的系统应至少具有 4 GB 的内存和高速互联网访问权限。

在运行安装之前，[设置您的亚马逊云科技 CLI](https://docs.amazonaws.cn/cli/latest/userguide/getting-started-quickstart.html)：
1. 在您的亚马逊云科技账户中设置具有完全管理员权限的 IAM 用户。
2. 使用亚马逊云科技访问密钥和默认区域设置您的 CLI 凭证。

为保证更好的用户体验并遵守中国的法律法规, 亚马逊在中国与持有相关电信牌照的本地合作伙伴开展技术合作，由本地合作伙伴向客户提供云服务。北京光环新网科技股份有限公司是 亚马逊云科技 北京区域云的服务运营方和提供方，宁夏西云数据科技有限公司是 亚马逊云科技 宁夏区域云的服务运营方和提供方。由于亚马逊云科技中国与亚马逊云科技全球区域分开运营，由于功能可用性和法规要求，**亚马逊云科技中国区**中的 SaaS Boost 预置体验是独一无二的。本文档提供了在**亚马逊云科技中国区**中预置 SaaS Boost 的指南。
1. 系统用户服务在以下方面有所不同：
    - Amazon Cognito 用户池目前在中国区域不可用。
    - Keycloak 作为开源身份和访问管理的提供程序。
2. 管理控制台 在以下方面有所不同：
    - 您不能使用默认 CloudFront 域*.cloudfront.cn,来提供内容。您必须向 CloudFront 分配添加备用域名（也称为别名记录），然后在内容 URL 中使用该域名。此外，由于中国地区合规原因，您还必须[ICP备案](https://www.amazonaws.cn/en/about-aws/china/#ICP_in_China)。此外，与全球 CloudFront 服务一样，要通过 HTTPS 提供内容，您必须使用带有备用域名的 SSL/TLS 证书。
    - 中国 CloudFront 地区的亚马逊目前不支持Amazon Certificate Manager。您必须从其他第三方证书颁发机构 (CA) 获取 SSL/TLS 证书，然后将其上传到 IAM 证书存储。
3. Amazon SES 在以下方面有所不同：
    - Amazon SES 在 **亚马逊云科技中国区** 区域中不可用。
4. Amazon QuickSight 在以下方面有所不同：
    - Amazon QuickSight 在 **亚马逊云科技中国区** 区域中不可用。

要准备安装过程，请根据需要执行以下步骤：
1. 您有一个域名，并且您的域名经过 [ICP备案](https://www.amazonaws.cn/en/about-aws/china/#ICP_in_China)。
2. 您需要一个根域名和单个域名来安装 Keycloak 和管理控制台。作为其中的一部分，您需要在 ACM 中为 keycloak 域提供一个证书，在 IAM 中为管理控制台 域提供一个证书。可以为通配符证书（例如 *.example.com）或特定证书（例如 keycloak.example.com）。
    - 您需要在 Route53 中为您的域创建一个公有托管区域。
    - 请求或上传 [证书至 ACM](https://docs.aws.amazon.com/acm/latest/userguide/gs-acm-request-public.html)
    - 上传 [证书至 IAM](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/cnames-and-https-procedures.html)

要开始安装过程，请执行以下步骤：
1. 从终端窗口中，导航到您下载了 SaaS Boost（AWS-SaaS-Boost）的目录。
2. 调用安装命令。
      - 如果您在 Linux 或 OSX 上运行，请运行：`sh ./install.sh`
      - 如果你在视窗上运行，请运行： `powershell .\install.ps1`
3. 选择新安装的选项。
4. 输入您的 SaaS Boost 目录的完整路径（点击当前目录的回车键） (hit enter for the current directory): /\<mydir\>/aws-saas-boost。
5. 输入此 SaaS Boost 环境的名称 （dev、QA、 test、 sandbox等）。
6. 输入 SaaS Boost 管理员的电子邮件地址，该管理员将收到初始临时密码。在亚马逊云科技中国区，管理员的初始临时密码将存储在 Secrets Manager 中的密钥`sb-{env}-admin`里.
7. 输入“Keycloak”作为身份提供程序，供系统用户使用。
8. 输入控制平面身份提供程序的域名。
9. 为 Keycloak 域选择 Route53 托管区域和 ACM 证书。
10. 输入 SaaS Boost 管理控制台的域名。
11. 为 SaaS Boost Admin UI 域选择 Route53 托管区域和 IAM 证书。
12. 指示您是否希望安装 SaaS Boost 的指标和分析功能。
此步骤是 ***可选的***，并将预配置一个[Redshift](https://aws.amazon.com/redshift)集群。
    - 暂时您只能输入**N**，因为QuickSight目前在亚马逊云科技中国区中不可用
13. 如果您的应用程序是基于 Windows 的并且需要共享文件系统，则必须部署 [托管 Active Directory](https://aws.amazon.com/directoryservice/) 以支持 [适用于 Windows 文件服务器的 Amazon FSx](https://aws.amazon.com/fsx/windows/) 或 [适用于 NetApp 的 Amazon ONTAP](https://aws.amazon.com/fsx/netapp-ontap/)。根据需要选择 y 或 n。
14. 查看安装的设置。仔细检查您将要在其中安装 SaaS Boost 的亚马逊云科技账号和区域。输入 **y** 继续，或输入 **n** 重新输入或调整值。

安装过程需要 30-45 分钟来配置和预配所有资源（这将根据所选的选项而有所不同）。安装过程中的详细日志存储在 **saas-boost-install.log**中。

## 步骤 4 - 登录 SaaS Boost
作为安装过程的一部分，您将在安装过程最后得到 SaaS Boost 管理控制台的 URL 链接。通过 Secrets Manager, 获取临时密码。使用您的网络浏览器导航到 SaaS Boost 管理控制台。出现以下登录：

![登录界面](images/gs-login.png?raw=true "登录界面")

输入 `admin` 作为用户名，并输入前面提到的临时密码。由于您使用临时密码登录，因此系统会提示您输入帐户的新密码。屏幕显示如下：

![修改密码](images/gs-change-password.png?raw=true "修改密码")

## 步骤 5 - 配置层级和应用程序设置
登录 SaaS Boost 管理应用程序后，您需要做的第一件事是配置环境，以使其与您的应用程序的运行需求保持一致。

SaaS Boost 支持基于 _层级_ 配置您的应用程序设置。层级允许您为不同的客户群打包您的 SaaS 产品。例如，除了您的标准层级产品之外，您可能还有一个试用层级或高级层级。 SaaS Boost 为您创建一个 ***default*** 层。如果您想重命名default层或创建其他层，请从屏幕左侧的导航中选择 **Tiers**。页面显示类似于以下内容：

![应用程序设置](images/gs-tiers.png?raw=true "层级")

单击 **Create Tier** 按钮以创建新层，或单击表列表中的层名称以编辑现有层。为方便起见，新创建的层最初将继承 __default__ 层的设置。有关如何利用层来优化 SaaS 应用程序交付的更多详细信息，请参阅用户指南。

现在您的 _层级_ 已定义，您需要为您的应用程序及其服务进行配置。从屏幕左侧的导航中选择 **Application**。页面显示类似于以下内容：

![应用程序设置](images/gs-app-setup.png?raw=true "应用程序设置")

首先为您的应用程序提供一个 **名称**。进行测试过程中您无需填写 **Domain Name** 或 **SSL Certificate** 条目。在生产中，确保您在 [Amazon Certificate Manager](https://aws.amazon.com/certificate-manager/)中为您将托管 SaaS 应用程序的域名定义了一个证书。请注意[注册域名](https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/domain-register.html)和[设置 SSL 证书](https://docs.aws.amazon.com/acm/latest/userguide/gs.html)必须在配置 SaaS Boost 之前完成。

SaaS Boost 让您可以根据需要进行配置，通过尽可能多的“服务”来支持您的工作负载。您为每个服务提供单独的 Docker 镜像。这些服务可以是公共的或私有的，并且彼此独立配置。服务之间不共享文件系统或数据库等基础设施资源，但它们可以在预置的 VPC 网络内相互通信。公有服务通过应用负载均衡器公开，并可通过互联网上的 DNS 访问。私有服务无法从互联网访问。请参阅开发人员和用户指南，深入了解如何使用服务来部署您的 SaaS 应用程序。

通过单击 **New Service** 按钮创建您的第一个服务。将出现类似于以下的弹出对话框：

![应用程序设置](images/gs-new-service.png?raw=true "新服务")

在此示例中，我将我的服务称为 `main`。单击弹出对话框中的**创建服务**按钮后，新服务将出现在类似于以下内容的服务列表中：

![应用程序设置](images/gs-service-collapsed.png?raw=true "Service Config Collapsed")

单击服务名称以展开该服务的配置选项。将出现类似于以下的表格：

![应用程序设置](images/gs-service-config.png?raw=true "Service Config")

虽然本 _入门指南_ 并未记录配置中的每个字段，但您选择的选项对于您的应用程序正常运行至关重要。

## 步骤 6 - 上传您的应用程序服务
为您定义的每一层级配置服务后，SaaS Boost 将自动创建一个 [Amazon ECR](https://docs.aws.amazon.com/AmazonECR/latest/userguide/what-is-ecr.html) 针对每个服务的镜像存储库。在将任何租户加入系统之前，您必须为应用程序中的每个服务上传 Docker 镜像。在 **Summary** 页面中，单击每个服务的 `ECR Repository URL` 列表旁边的 **View details** 链接，以查看上传图像的正确 Docker 推送命令。您还可以参考示例应用程序中包含的 shell 脚本，来了解一种自动化推送 Docker 的方法。

## 步骤 7 - （可选）部署示例应用
本节介绍上传示例应用程序的过程，让您了解此过程的工作原理。作为 SaaS Boost 存储库的一部分，我们提供了一个简单的示例应用程序。

### 为示例应用程序配置 SaaS Boost
正如您将配置 SaaS Boost 以支持您的应用程序的要求一样，我们必须为此示例应用程序进行正确设置。此示例应用程序依赖于 SaaS Boost 中的以下配置。
- 为应用程序输入一个友好的名称 `Name`，例如 **Sample**
- 创建一个 `New Service` 并给它一个名称，例如 **main**
- 确保 `Publicly access` 框被**选中**
- 确保 `Service Addressable Path` 是 **/\***
- 对于 `Container OS`，选择 **Linux**
- 将 `Container Tag` 设置为**latest**
- 将 `Container Port` 设置为 **8080**
- 将 `Health Check URL` 设置为 **/index.html**
- 在 `default` 层设置下，将 `Compute Size` 设置为 **Medium**
- `Minimum Instance Count` 和 `Maximum Instance Count` 可以分别为 **1** 和 **2**
- **启用** `Provision a File System for the application` 复选框
- 将 `Mount point` 设置为 **/mnt**
- **启用** `Provision a File System for the application` 复选框
- 选择任何可用的数据库（具有 db.t3.micro 实例类的 MariaDB 将提供最快的启动速度）
- 输入 **数据库名称**、**用户名**和**密码**。您 _不需要_ 为数据库初始化提供 SQL 文件。

您的配置应类似于以下内容：

![示例应用程序配置](images/gs-sample-app-config.png?raw=true "示例应用程序配置")

### 构建和部署示例应用程序
保存应用程序设置后，您可以构建和容器化示例应用程序。要创建此示例应用程序的 Docker 镜像，您需要在本地计算机上运行 Docker。导航到您的 SaaS Boost 存储库克隆中的目录 **samples/java/** 并执行脚本，这将构建、容器化并将您的应用程序推送到 SaaS Boost ECR 存储库。您可以查看此示例 shell 脚本中的步骤，了解如何通过此构建过程使您的应用程序与 SaaS Boost 灵活集成。

```shell
cd aws-saas-boost/samples/java
sh build.sh
```

此脚本会提示您提供 SaaS Boost 环境。输入您在运行 SaaS Boost 安装程序时指定的环境标签。然后该脚本将提示您输入您正在构建的服务的名称。在本例中，使用 `main`。请注意，服务名称区分大小写。此脚本完成后，您已将应用程序服务发布到 SaaS Boost 应用程序存储库。当您对应用程序进行更改时，您可以再次执行构建脚本来更新您的应用程序。

对于多服务应用程序，您需要为您配置的每个服务重复相同的步骤。

### 租户入驻
您现在可以加入一个新租户，为该租户提供所有必要的基础架构来支持您的应用程序并部署您配置的所有服务。导航到管理应用程序的 **Onboarding** 页面，然后单击 **Provision Tenant** 按钮。入驻流程完成后，您将能够访问该租户的示例应用程序，方法是导航到管理应用程序的 **Tenants** 页面，然后转到租户详细信息页面，最后单击 **Load Balancer DNS** 链接。

## 将您的应用程序迁移到 SaaS Boost
本指南简要介绍了设置 SaaS Boost 所需的基本步骤。当您考虑将工作负载转移到 SaaS Boost 中时，请详细地参阅 SaaS Boost 更多功能。您需要仔细地查看您的应用程序的配置方案，以及它们如何适配到不同的 SaaS Boost 应用程序设置。

在熟悉了以上步骤之后，您可以继续通过查看 SaaS Boost [用户指南](user-guide.md) 和 [开发人员指南](developer-guide.md) 来更好地配置 SaaS Boost 模型。

容器化工作负载所需的步骤因应用程序的性质而异。您可以使用 SaaS Boost 提供的示例应用程序中的 Dockerfile 示例。以下是一些提供有关容器化应用程序信息的其他资源：
- [亚马逊云科技学习路径：容器化单体应用](https://aws.amazon.com/getting-started/container-microservices-tutorial/module-one/)
- [亚马逊云科技 App2Container](https://aws.amazon.com/app2container/)
