import boto3
import base64
import json
import logging
from botocore.exceptions import ClientError
from datetime import datetime, timedelta
from urllib.parse import urlencode
from urllib.request import Request, urlopen
#from urllib.request import HTTPHandler, HTTPSHandler, build_opener, install_opener

logger = logging.getLogger()

class SaaSBoostApiHelper:
  
  __credentials_cache = {}
  
  def __init__(self, app_client_secret_arn):
    # Get the OAuth application client details from the SaaS Boost control
    # plane Secrets Manager entry
    self.secrets = boto3.client('secretsmanager')
    try:
      api_secret = self.secrets.get_secret_value(SecretId=app_client_secret_arn)
      client_details = json.loads(api_secret['SecretString'])
      self.client_name = client_details['client_name']
      self.client_id = client_details['client_id']
      self.client_secret = client_details['client_secret']
      self.token_endpoint = client_details['token_endpoint']
      self.api_endpoint = client_details['api_endpoint']
      logger.debug("Fetched API client details from Secrets Manager")
    except ClientError as secrets_manager_error:
      logger.error("Error fetching API client secret from SaaS Boost control plane")
      logger.error(str(secrets_manager_error))
      raise
  
  def authorized_request(self, method, resource, body=None):
    if not resource.startswith('/'):
      resource = '/' + resource
    api_request = Request(
      url=self.api_endpoint + resource,
      method=method,
      data=body.encode() if body else None
    )
    api_request.add_header('Authorization', self.__bearer_token())
    api_request.add_header('Content-Type', 'application/json')

    #http_handler = HTTPHandler(debuglevel=1)
    #https_handler = HTTPSHandler(debuglevel=1)
    #opener = build_opener(http_handler, https_handler)
    #install_opener(opener)

    with urlopen(api_request) as api_response:
      response_data = api_response.read()
      if response_data:
        return json.loads(response_data.decode())
      else:
        return
  
  def __get_cached_credentials(self):
    cached = self.__credentials_cache.get(self.client_id)
    if cached:
      exp = datetime.fromtimestamp(cached['expiry'])
      time_buffer = 2
      if datetime.now() + timedelta(seconds=time_buffer) < exp:
        logger.debug(f"Client credentials cache hit {self.client_id}")
        return cached
      else:
        logger.debug(f"Cached credentials expiring in < {time_buffer}s {self.client_id}")
    else:
      logger.debug(f"Client credentials cache miss {self.client_id}")
    return None
  
  def __put_cached_credentials(self, grant):
    self.__credentials_cache[self.client_id] = {
      'expiry': (datetime.now() + timedelta(seconds=grant['expires_in'])).timestamp(),
      'access_token': grant['access_token']
    }
  
  def __bearer_token(self):
    token = self.__get_cached_credentials()
    if not token:
      token = self.__client_credentials_grant()
      self.__put_cached_credentials(token)
    return f"Bearer {token['access_token']}"
  
  def __client_credentials(self):
    # Generate a Base64 encoded string of the client credential
    return base64.b64encode(f"{self.client_id}:{self.client_secret}".encode()).decode()
  
  def __client_credentials_grant(self):
    # Generate the encoded client secret string
    client_secret = self.__client_credentials()
    
    # POST to the token endpoint a client_credentials grant
    token_request = Request(
      url=self.token_endpoint,
      data=urlencode({"grant_type": "client_credentials"}).encode(),
      method='POST'
    )
    token_request.add_header('Authorization', 'Basic ' + client_secret)
    token_request.add_header('Content-Type', 'application/x-www-form-urlencoded')
    
    with urlopen(token_request) as token_response:
      # {'expires_in': seconds, 'token_type': 'Bearer', 'access_token': jwt}
      grant = json.loads(token_response.read().decode())
      logger.debug(grant)
      return grant
  
