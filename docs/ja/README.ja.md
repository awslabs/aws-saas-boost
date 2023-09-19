### [English](../../README.md) | 日本語

# AWS SaaS Boost

## Overview
AWS SaaS Boost は、クラウドで SaaS ワークロードを正常に実行するための、すぐに使えるコアソフトウェコンポーネントを組織に提供します。ソフトウェア開発者は、AWS SaaS Boost を使用して SaaS ソリューションを構築する際の複雑さを排除することで、中核となる知的財産の保護に集中できます。

SaaS Boostは、テナント管理、デプロイメントの自動化、分析ダッシュボード、請求とメータリング、管理用 Web インターフェイスなど、すぐに使える機能を備えています。この環境により、開発と実験にかかる時間が短縮され、ソフトウェアをより早く顧客に届けることができます。

[入門ガイド](./getting-started.ja.md) から、今すぐ AWS SaaS ブーストの使用を開始できます!

[フィードバックをお寄せください!](https://www.pulse.aws/survey/YOUHWDCP)

## レポジトリの概要

| ディレクトリ | 説明 |
| --- | --- |
| client/web | アドミンウェブアプリケーション |
| docs | ドキュメント |
| docs/images | ドキュメント用の画像 |
| functions | ヘルパー Lambda 関数 |
| functions/core-stack-listener | CloudFormation-> SNS からのコールバックによりトリガされる、アプリケーションサービスのリソースを動的に作成する CloudFormation マクロ  |
| functions/ecs-service-update | CodePipeline によって使用され、ECS が少なくとも 1 つのタスクをデプロイしていることを確認 |
| functions/ecs-shutdown-services | 非本番環境でのコスト削減のためにテナント ECS サービスをシャットダウンするオプション機能 |
| functions/ecs-startup-services | シャットダウンされたテナント ECS サービスを起動するためのオプション機能 |
| functions/onboarding-app-stack-listener | CloudFormation-> SNSからのコールバックによりトリガーされる、各アプリケーションサービスのポストプロビジョニングフロー |
| functions/onboarding-stack-listener | CloudFormation-> SNS からのコールバックによりトリガーされる、テナントのプロビジョニング後のフロー |
| functions/system-rest-api-client | サービスが他のサービスの API を呼び出すために使用する REST クライアント |
| functions/workload-deploy | ECR とオンボーディングサービスの変更を監視して、テナントの CodePipeline をトリガー |
| installer | コマンドラインインストーラー |
| layers | Lambda レイヤー (i.e. シャードライブラリー) |
| layers/apigw-helper | REST クライアントが API Gateway エンドポイントを呼び出すために使用。プライベートシステム API の SigV4 リクエスト署名をサポート |
| layers/cloudformation-utils | クラウドフォーメーションユーティリティ機能 |
| layers/utils | ユーティリティ機能 |
| metering-billing | オプションのビリングおよびメータリングモジュール |
| metering-billing/lambdas | ビリングサービス |
| metrics-analytics | オプションの分析モジュール |
| metrics-analytics/deploy | Redshift へ書き込む用の Kinesis Firehose JSONPath |
| metrics-analytics/metrics-generator | サンプルとしてメトリクスを作成してプッシュするテストスクリプト |
| metrics-analytics/metrics-java-sdk | メトリクスペイロードを構築して SaaS Boost にプッシュするためのサンプル Java ライブラリ |
| resources | CloudFormation リソース |
| resources/custom-resources | CloudFormation カスタムリソース |
| resources/custom-resources/app-services-ecr-macro | 定義された各アプリケーションサービスの ECR リポジトリと関連インフラストラクチャを動的に生成する CloudFormation マクロ |
| resources/custom-resources/cidr-dynamodb | テナント VPC で使用可能な CIDR ブロックを DynamoDB テーブルに入力 |
| resources/custom-resources/clear-s3-bucket | すべてのファイルのすべてのバージョンとマーカーを S3 バケットから削除し、これにより、CloudFormation はスタックの削除時にバケットを削除可能。 |
| resources/custom-resources/fsx-dns-name | ホストされている FSx ファイルシステムの DNS エントリを取得 |
| resources/custom-resources/rds-bootstrap | テナントのオンボーディング中に SQL ファイルを実行して空のデータベースをブートストラップ |
| resources/custom-resources/rds-options | 現在のリージョンとアカウントで使用可能な RDS エンジン、バージョン、インスタンスタイプをキャッシュ |
| resources/custom-resources/redshift-table | オプションの分析モジュール用に RedShift データベースをブートストラップ |
| resources/custom-resources/set-instance-protection | スタックを更新または削除したときの AutoScaling インスタンス保護を無効化 |
| samples | SaaS Boost にアプリケーションとしてデプロイできるワークロードの例 |
| samples/java | Java Spring Framework Web MVC を使用した Linux のサンプルモノリシックアプリケーション |
| samples/dotnet-framework | .NET Framework 4.x ASP.NET MVC (.NET Core ではない) を使用した Windows OS のサンプルモノリシックアプリケーション
| services | SaaS Boost マイクロサービス |
| services/metric-service | Metrics Service にて、アドミンウェブアプリケーションのオペレーションインサイトダッシュボードをサポート |
| services/onboarding-service | オンボーディングサービスは、テナントの作成、インフラストラクチャのプロビジョニング、ワークロードのオンボーディング、ビリング設定をオーケストレート |
| services/quotas-service | クォータサービスは、新しいテナントをオンボーディングする前に AWS アカウントのサービスクォータをチェック |
| services/settings-service | 設定サービスは、SaaS Boost環境構成とアプリケーション構成を維持 |
| services/tenant-service | テナントサービスは、テナントとその固有の属性を管理 |
| services/tier-service | ティアサービスは、サービスをパッケージ化するためのティアを管理。アプリケーション構成はティアごとに定義可能 |
| services/user-service | ユーザーサービスはシステムユーザー (アプリケーションのユーザーではなく、アドミンウェブアプリのユーザー) を管理 |

## コスト
AWS SaaS Boost によって利用される [さまざまなサービス](docs/services.md)に対して料金が請求されます。プロビジョニングされたリソースには「SaaS Boost」のタグが付けられ、各テナントのリソースにも [コスト配分](https://docs.aws.amazon.com/awsaccountbilling/latest/aboutv2/cost-alloc-tags.html)に役立つように一意のタグが付けられています。

AWS SaaS Boost 環境にオンボーディングされる各テナントは、より多くのインフラストラクチャをプロビジョニングし、それに伴いコストも増加します。

_optional_ アナリティクスとメトリクスモジュールは Redshift クラスターをプロビジョニングすることに注意してください。

## セキュリティ

詳細については、[コントリビューティング](CONTRIBUTING.md #security-issue-notifications)を参照してください。

## ライセンス

このプロジェクトは Apache-2.0 ライセンスの下でライセンスされています。[LICENSE](ライセンス)ファイルを参照してください。
