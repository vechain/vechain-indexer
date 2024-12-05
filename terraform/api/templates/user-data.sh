#! /bin/bash
set -x
# allow magic-sysreq on first boot to allow 
echo "1" > /proc/sys/kernel/sysrq
#v- ec2-user serial console access 
#echo "${admin_password}" | passwd --stdin ec2-user
#cloud-boothook
exec > >(tee /var/log/user-data.log|logger -t user-data -s 2>/dev/console) 2>&1
sysctl -w vm.max_map_count=131068
echo "vm.max_map_count=131068" | tee -a /etc/sysctl.conf
# Mount the EBS volume to /mnt/data
cat /proc/partitions
mkfs -t xfs /dev/sdg
mkdir /mnt/data
mount /dev/sdg /mnt/data
echo "/dev/sdg /mnt/data xfs defaults,nofail 0 2" >> /etc/fstab
yum update -y
yum -y install \
    ca-certificates \
    curl \
    gnupg \
    unzip \
    lsb-release 
# add awscli
export ARCH=$(uname -m | sed 's/amd64/x86_64/')
curl "https://awscli.amazonaws.com/awscli-exe-linux-$ARCH.zip" -o "awscliv2.zip"
unzip awscliv2.zip
./aws/install
aws configure set region ${awsregion}
# add ssm access
yum install -y https://s3.amazonaws.com/ec2-downloads-windows/SSMAgent/latest/linux_arm64/amazon-ssm-agent.rpm
adduser -m ssm-user
tee /etc/sudoers.d/ssm-agent-users <<'EOF'
# User rules for ssm-user
ssm-user ALL=(ALL) NOPASSWD:ALL
EOF
chmod 440 /etc/sudoers.d/ssm-agent-users 
systemctl enable amazon-ssm-agent
systemctl start amazon-ssm-agent
systemctl daemon-reload
systemctl status amazon-ssm-agent
tee /etc/cron.daily/yum-update <<'EOF1'
#!/bin/bash 
yum update -y
EOF1
chmod 755 /etc/cron.daily/yum-update
# Add Docker’s official GPG key
mkdir -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
#curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
# Setup the repository:
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
# Update the apt package index, and install the latest version of Docker Engine, containerd, and Docker Compose, or go to the next step to install a specific version
yum update
# Install Docker
yum install -y docker
# Add group membership so you can run docker commands without sudo
usermod -a -G docker ec2-user
id ec2-user
# Reload a linux user's group assignments to docker w/o logout
newgrp docker
chmod 666 /var/run/docker.sock

# configure docker log rotation
mkdir -p /etc/docker
cat <<'EOF2' > /etc/docker/daemon.json
{
  "log-driver": "awslogs",
  "log-opts": { "awslogs-region": "${awsregion}", "awslogs-group": "${log_group}" }
}
EOF2

# Enable docker service at AMI boot time:
systemctl enable docker.service
# Start the Docker service
systemctl start docker.service
systemctl daemon-reload 
# Install Docker Compose
curl -L https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m) -o /usr/local/bin/docker-compose
chmod +x /usr/local/bin/docker-compose
umask 0002
mkdir -p /mnt/data/indexerdb
# create bootstrap scripts
mkdir -p /mnt/data/scripts
cat <<'EOF3' > /mnt/data/scripts/init.js
printjson(rs.initiate())
while (true) {
  var status = rs.isMaster()
  if (status && status.ismaster) {
      break
  }
  sleep(1000)
}
function createUser(db, username, password, roles) {
  if (db.system.users.find({user: username}).count() <= 0) {
      console.log( "Add (" + username + ") +roles: " + JSON.stringify(roles));
      db.createUser({
          user: username,
          pwd: password,
          roles: roles,
      });
  } else {
      console.log( "(" + username + ") here-Updating");
      db.updateUser(username, {
          pwd: password,
          roles: roles,
      });
  }
}

adminDb = db.getSiblingDB("admin");
createUser( adminDb, "${admin_username}", "${admin_password}", [ { role: "root", db: "admin", }, ]);
createUser( adminDb, "${indexer_username}", "${indexer_password}", [ { role: "readWrite", db: "vechain", }, ]);
createUser( adminDb, "${api_username}", "${api_password}", [{role: "read", db: "vechain"}]);
console.log("Add db vechain");
db = db.getSiblingDB("vechain");
EOF3
chmod 755 /mnt/data/scripts/init.js
cat <<'EOF4' > /mnt/data/scripts/setup.sh
#!/bin/bash
export GENESIS_BLOCK_ID=0x00000000c05a20fbca2bf6ae3affba6af4a74b800b585bf7a4988aba7aea69f6
sleep 5
mongosh --host localhost:27017 -u "${admin_username}" -p "${admin_password}" /data/scripts/init.js
echo -e " Mongodb initialisation returned $?"
EOF4
chmod 755 /mnt/data/scripts/setup.sh
#TODO: resolve initial load of builtin contracts
chown -R ec2-user /mnt/data/
chmod -R u+w /mnt/data/
# Move into persistent storage volume for MongoDB
cd /mnt/data

# if the keyfile doesn't exist
mkdir -p /mnt/data/keys
openssl rand -base64 756 > keys/keyfile
sudo chmod 400 keys/keyfile
sudo chown 999:999 keys/keyfile

# Create a docker-compose.yml file
cat <<'EOF5' > /mnt/data/docker-compose.yml
---
version: '3.5'

services:
  mongo:
    image: mongo:8
    container_name: mongodb
    hostname: mongo-node1
    command: [ "--replSet", "rs0", "--keyFile", "/data/keys/keyfile" ]
    ulimits:
      nofile:
        soft: 65536
        hard: 65536
    restart: always
    expose:
      - 27017
    ports:
      - "27017:27017"
    volumes:
      - /mnt/data:/data
      - /mnt/data/configdb:/data/configdb
      - /mnt/data/db:/data/db
      - /mnt/data/keys:/data/keys
    healthcheck:
      test: [ "CMD","mongosh", "--eval", "db.adminCommand('ping')" ]
      interval: 3s
      timeout: 5s
      retries: 20
    environment:
      MONGO_INITDB_ROOT_USERNAME: "${admin_username}"
      MONGO_INITDB_ROOT_PASSWORD: "${admin_password}"
      MONGO_INITDB_DATABASE: vechain
    networks:
      - mongodb

networks:
  mongodb:
    driver: bridge
    name: mongodb

volumes:
  data:
EOF5
# 

rm -rf mongo.key mongod.conf
# Let's create mongodb key file
#openssl rand -base64 756 > mongo.key
#chmod 400 mongo.key
# Let's create mongodb config file
cat <<'EOF6' > /mnt/data/mongod.conf
security:
  authorization: disabled
storage:
  dbpath: /data/indexerdb
EOF6
# Let's start the MongoDB container
aws ecr get-login-password --region eu-west-1 | docker login --username AWS --password-stdin 937628727224.dkr.ecr.eu-west-1.amazonaws.com
docker-compose -f /mnt/data/docker-compose.yml up --wait -d --build mongo
# Let's check the status of the MongoDB container
docker ps  -a  --filter name=mongo
# Create a user on the database with the following command:
docker exec -i mongodb bash -c "/data/scripts/setup.sh"
hostnamectl set-hostname ${hostname}
systemctl restart amazon-ssm-agent
#done
echo "End User-Data.sh"
