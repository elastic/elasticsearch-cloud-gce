Google Compute Engine Cloud Plugin for Elasticsearch
====================================================

The GCE Cloud plugin allows to use GCE API for the unicast discovery mechanism.

In order to install the plugin, run: 

```sh
bin/plugin -install elasticsearch/elasticsearch-cloud-gce/2.3.0
```

You need to install a version matching your Elasticsearch version:

|       Elasticsearch    | GCE Cloud Plugin  |                                                             Docs                                                                   |
|------------------------|-------------------|------------------------------------------------------------------------------------------------------------------------------------|
|    master              | Build from source | See below                                                                                                                          |
|    es-1.x              | Build from source | [2.5.0-SNAPSHOT](https://github.com/elasticsearch/elasticsearch-cloud-gce/tree/es-1.x/#google-compute-engine-cloud-plugin-for-elasticsearch)|
|    es-1.4              | Build from source | [2.4.0-SNAPSHOT](https://github.com/elasticsearch/elasticsearch-cloud-gce/tree/es-1.4/#google-compute-engine-cloud-plugin-for-elasticsearch)|
|    es-1.3              |     2.3.0         | [2.3.0](https://github.com/elasticsearch/elasticsearch-cloud-gce/tree/v2.3.0/#version-230-for-elasticsearch-13)                  |
|    es-1.2              |     2.2.0         | [2.2.0](https://github.com/elasticsearch/elasticsearch-cloud-gce/tree/v2.2.0/#google-compute-engine-cloud-plugin-for-elasticsearch)|
|    es-1.1              |     2.1.2         | [2.1.2](https://github.com/elasticsearch/elasticsearch-cloud-gce/tree/v2.1.2/#google-compute-engine-cloud-plugin-for-elasticsearch)|
|    es-1.0              |     2.0.1         | [2.0.1](https://github.com/elasticsearch/elasticsearch-cloud-gce/tree/v2.0.1/#google-compute-engine-cloud-plugin-for-elasticsearch)|
|    es-0.90             |     1.3.0         | [1.3.0](https://github.com/elasticsearch/elasticsearch-cloud-gce/tree/v1.3.0/#google-compute-engine-cloud-plugin-for-elasticsearch)|

To build a `SNAPSHOT` version, you need to build it with Maven:

```bash
mvn clean install
plugin --install cloud-gce \ 
       --url file:target/releases/elasticsearch-cloud-gce-X.X.X-SNAPSHOT.zip
```


Google Compute Engine Virtual Machine Discovery
===============================

Google Compute Engine VM discovery allows to use the google APIs to perform automatic discovery (similar to multicast in non hostile
multicast environments). Here is a simple sample configuration:

```yaml
  cloud:
      gce:
          project_id: <your-google-project-id>
          zone: <your-zone>
  discovery:
          type: gce
```

How to start (short story)
--------------------------

* Create Google Compute Engine instance (with compute rw permissions)
* Install Elasticsearch
* Install Google Compute Engine Cloud plugin
* Modify `elasticsearch.yml` file
* Start Elasticsearch

How to start (long story)
--------------------------

### Prerequisites

Before starting, you should have:

* Your project ID. Let's say here `es-cloud`. Get it from [Google APIS Console](https://code.google.com/apis/console/).
* [Google Cloud SDK](https://developers.google.com/cloud/sdk/)

If you did not set it yet, you can define your default project you will work on:

```sh
gcloud config set project es-cloud
```

### Creating your first instance


```sh
gcutil addinstance myesnode1 \
       --service_account_scope=compute-rw,storage-full \
       --persistent_boot_disk
```

You will be asked to open a link in your browser. Login and allow access to listed services.
You will get back a verification code. Copy and paste it in your terminal.

You should get `Authentication successful.` message.

Then, choose your zone. Let's say here that we choose `europe-west1-a`.

Choose your compute instance size. Let's say `f1-micro`.

Choose your OS. Let's say `projects/debian-cloud/global/images/debian-7-wheezy-v20140606`.

You may be asked to create a ssh key. Follow instructions to create one.

When done, a report like this one should appears:

```sh
Table of resources:

+-----------+--------------+-------+---------+--------------+----------------+----------------+----------------+---------+----------------+
|   name    | machine-type | image | network |  network-ip  |  external-ip   |     disks      |      zone      | status  | status-message |
+-----------+--------------+-------+---------+--------------+----------------+----------------+----------------+---------+----------------+
| myesnode1 | f1-micro     |       | default | 10.240.20.57 | 192.158.29.199 | boot-myesnode1 | europe-west1-a | RUNNING |                |
+-----------+--------------+-------+---------+--------------+----------------+----------------+----------------+---------+----------------+
```

You can now connect to your instance:

```
# Connect using google cloud SDK
gcloud compute ssh myesnode1 --zone europe-west1-a

# Or using SSH with external IP address
ssh -i ~/.ssh/google_compute_engine 192.158.29.199
```

*Note Regarding Service Account Permissions*

It's important when creating an instance that the correct permissions are set. At a minimum, you must ensure you have:

```
service_account_scope=compute-rw
```

Failing to set this will result in unauthorized messages when starting Elasticsearch. 
See [Machine Permissions](#machine-permissions).

Once connected, install Elasticsearch:

```sh
sudo apt-get update

# Download Elasticsearch
wget https://download.elasticsearch.org/elasticsearch/elasticsearch/elasticsearch-1.2.1.deb

# Prepare Java installation
sudo apt-get install java7-runtime-headless

# Prepare Elasticsearch installation
sudo dpkg -i elasticsearch-1.2.1.deb
```

### Install elasticsearch cloud gce plugin

Install the plugin:

```sh
# Use Plugin Manager to install it
sudo /usr/share/elasticsearch/bin/plugin --install elasticsearch/elasticsearch-cloud-gce/2.2.0

# Configure it:
sudo vi /etc/elasticsearch/elasticsearch.yml
```

And add the following lines:

```yaml
cloud:
  gce:
      project_id: es-cloud
      zone: europe-west1-a
discovery:
      type: gce
```


Start elasticsearch:

```sh
sudo /etc/init.d/elasticsearch start
```

If anything goes wrong, you should check logs:

```sh
tail -f /var/log/elasticsearch/elasticsearch.log
```

If needed, you can change log level to `TRACE` by modifying `sudo vi /etc/elasticsearch/logging.yml`:

```yaml
  # discovery
  discovery.gce: TRACE
```



### Cloning your existing machine

In order to build a cluster on many nodes, you can clone your configured instance to new nodes.
You won't have to reinstall everything!

First create an image of your running instance and upload it to Google Cloud Storage:

```sh
# Create an image of yur current instance
sudo /usr/bin/gcimagebundle -d /dev/sda -o /tmp/

# An image has been created in `/tmp` directory:
ls /tmp
e4686d7f5bf904a924ae0cfeb58d0827c6d5b966.image.tar.gz

# Upload your image to Google Cloud Storage:
# Create a bucket to hold your image, let's say `esimage`:
gsutil mb gs://esimage

# Copy your image to this bucket:
gsutil cp /tmp/e4686d7f5bf904a924ae0cfeb58d0827c6d5b966.image.tar.gz gs://esimage

# Then add your image to images collection:
gcutil addimage elasticsearch-1-2-1 gs://esimage/e4686d7f5bf904a924ae0cfeb58d0827c6d5b966.image.tar.gz

# If the previous command did not work for you, logout from your instance
# and launch the same command from your local machine.
```

### Start new instances

As you have now an image, you can create as many instances as you need:

```sh
# Just change node name (here myesnode2)
gcutil addinstance --image=elasticsearch-1-2-1 myesnode2

# If you want to provide all details directly, you can use:
gcutil addinstance --image=elasticsearch-1-2-1 \
       --kernel=projects/google/global/kernels/gce-v20130603 myesnode2 \
       --zone europe-west1-a --machine_type f1-micro --service_account_scope=compute-rw \
       --persistent_boot_disk
```

### Remove an instance (aka shut it down)

You can use [Google Cloud Console](https://cloud.google.com/console) or CLI to manage your instances:

```sh
# Stopping and removing instances
gcutil deleteinstance myesnode1 myesnode2 \
       --zone=europe-west1-a

# Consider removing disk as well if you don't need them anymore
gcutil deletedisk boot-myesnode1 boot-myesnode2  \
       --zone=europe-west1-a
```

Using zones
-----------

`cloud.gce.zone` helps to retrieve instances running in a given zone. It should be one of the 
[GCE supported zones](https://developers.google.com/compute/docs/zones#available).

The GCE discovery can support multi zones although you need to be aware of network latency between zones. 
To enable discovery across more than one zone, just enter add your zone list to `cloud.gce.zone` setting:
 
```yaml
  cloud:
      gce:
          project_id: <your-google-project-id>
          zone: ["<your-zone1>", "<your-zone2>"]
  discovery:
          type: gce
```



Filtering by tags
-----------------

The GCE discovery can also filter machines to include in the cluster based on tags using `discovery.gce.tags` settings.
For example, setting `discovery.gce.tags` to `dev` will only filter instances having a tag set to `dev`. Several tags
set will require all of those tags to be set for the instance to be included.

One practical use for tag filtering is when an GCE cluster contains many nodes that are not running
elasticsearch. In this case (particularly with high ping_timeout values) there is a risk that a new node's discovery
phase will end before it has found the cluster (which will result in it declaring itself master of a new cluster
with the same name - highly undesirable). Adding tag on elasticsearch GCE nodes and then filtering by that
tag will resolve this issue.

Add your tag when building the new instance:

```sh
gcutil --project=es-cloud addinstance myesnode1 \
       --service_account_scope=compute-rw \
       --persistent_boot_disk \
       --tags=elasticsearch,dev
```

Then, define it in `elasticsearch.yml`:

```yaml
cloud:
  gce:
      project_id: es-cloud
      zone: europe-west1-a
discovery:
      type: gce
      gce:
            tags: elasticsearch, dev
```

Changing default transport port
-------------------------------

By default, elasticsearch GCE plugin assumes that you run elasticsearch on 9300 default port.
But you can specify the port value elasticsearch is meant to use using google compute engine metadata `es_port`:

### When creating instance

Add `--metadata=es_port:9301` option:

```sh
# when creating first instance
gcutil addinstance myesnode1 \
       --service_account_scope=compute-rw,storage-full \
       --persistent_boot_disk \
       --metadata=es_port:9301

# when creating an instance from an image
gcutil addinstance --image=elasticsearch-1-0-0-RC1 \
       --kernel=projects/google/global/kernels/gce-v20130603 myesnode2 \
       --zone europe-west1-a --machine_type f1-micro --service_account_scope=compute-rw \
       --persistent_boot_disk --metadata=es_port:9301
```

### On a running instance

```sh
# Get metadata fingerprint
gcutil getinstance myesnode1 --zone=europe-west1-a
+------------------------+---------------------------------------------------------------------------------------------------------+
|        property        |                                                  value                                                  |
+------------------------+---------------------------------------------------------------------------------------------------------+
| metadata               |                                                                                                         |
| fingerprint            | 42WmSpB8rSM=                                                                                            |
+------------------------+---------------------------------------------------------------------------------------------------------+

# Use that fingerprint
gcutil setinstancemetadata myesnode1 \
       --zone=europe-west1-a \
       --metadata=es_port:9301 \
       --fingerprint=42WmSpB8rSM=
```


Tips
----

### Store project id locally

If you don't want to repeat the project id each time, you can save it in `~/.gcutil.flags` file using:

```sh
gcutil getproject --project=es-cloud --cache_flag_values
```

`~/.gcutil.flags` file now contains:

```
--project=es-cloud
```

### Machine Permissions

**Creating machines with gcutil**

Ensure the following flags are set:

````
--service_account_scope=compute-rw
```

**Creating with console (web)**

When creating an instance using the web portal, click **Show advanced options**. 

At the bottom of the page, under `PROJECT ACCESS`, choose `>> Compute >> Read Write`.

**Creating with knife google**

Set the service account scopes when creating the machine:

```
$ knife google server create www1 \
    -m n1-standard-1 \
    -I debian-7-wheezy-v20131120 \
    -Z us-central1-a \
    -i ~/.ssh/id_rsa \
    -x jdoe \
    --gce-service-account-scopes https://www.googleapis.com/auth/compute.full_control
```

Or, you may use the alias:

```
    --gce-service-account-scopes compute-rw
```

If you have created a machine without the correct permissions, you will see `403 unauthorized` error messages. The only 
way to alter these permissions is to delete the instance (NOT THE DISK). Then create another with the correct permissions.


Testing
=======

Integrations tests in this plugin require working GCE configuration and therefore disabled by default.
To enable tests prepare a config file elasticsearch.yml with the following content:

```
cloud:
  gce:
      project_id: es-cloud
      zone: europe-west1-a
discovery:
      type: gce
```

Replaces `project_id` and `zone` with your settings. 

To run test:

```sh
mvn -Dtests.gce=true -Dtests.config=/path/to/config/file/elasticsearch.yml clean test
```


License
-------

This software is licensed under the Apache 2 license, quoted below.

    Copyright 2009-2014 Elasticsearch <http://www.elasticsearch.org>

    Licensed under the Apache License, Version 2.0 (the "License"); you may not
    use this file except in compliance with the License. You may obtain a copy of
    the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
    License for the specific language governing permissions and limitations under
    the License.
