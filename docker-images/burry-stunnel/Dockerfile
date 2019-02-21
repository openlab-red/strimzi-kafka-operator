FROM strimzi/stunnel-base:latest

# copy scripts for starting Stunnel
COPY ./scripts/ $STUNNEL_HOME

RUN chmod -R +x $STUNNEL_HOME/*.sh

USER 1001

CMD ["/opt/stunnel/stunnel_run.sh"]
