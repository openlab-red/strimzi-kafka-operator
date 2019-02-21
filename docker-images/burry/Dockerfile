FROM golang:1.10-alpine3.7

RUN apk add --no-cache git upx \
    && go get github.com/pwaller/goupx \
    && go get -d github.com/openlab-red/burry.sh \
    && cd /go/src/github.com/openlab-red/burry.sh \
    && CGO_ENABLED=0 GOOS=linux go build -a -installsuffix nocgo -o burry.sh \
    && goupx burry.sh

FROM centos:7

COPY --from=0 /go/src/github.com/openlab-red/burry.sh/burry.sh /usr/local/bin/burry
ENV HOME=/home/burry

RUN yum install -y centos-release-scl-rh && \
    INSTALL_PKGS="rsync tar unzip which zip bzip2" && \
    yum install -y --setopt=tsflags=nodocs $INSTALL_PKGS && \
    rpm -V  $INSTALL_PKGS $x86_EXTRA_RPMS && \
    yum clean all

RUN chown -R 1001:0 /usr/local/bin/burry && \
    chmod -R g+rwx /usr/local/bin/burry

RUN mkdir $HOME \
    && chown -R 1001:0 $HOME \
    && chmod -R g+rw $HOME

WORKDIR $HOME

USER 1001

ENTRYPOINT [ "/usr/local/bin/burry" ]
CMD [ "--help" ]
