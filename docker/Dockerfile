# Filename: Dockerfile
ARG TAG=latest
FROM postgres:${TAG}

ARG TAG
ENV PG_VERSION=${TAG}
ENV PG_BRANCH=REL_${TAG}_STABLE
ENV PLUGIN_BRANCH=print-vars-${TAG}

RUN export TAG=$PG_VERSION \
    && apt --yes update  \
    && apt --yes upgrade \
    && apt --yes install  \
    git build-essential  \
    libreadline-dev  \
    zlib1g-dev bison  \
    libkrb5-dev flex  \
    postgresql-server-dev-$PG_VERSION \
    && cd /usr/src/ \
    && git clone https://github.com/postgres/postgres.git \
    && cd postgres \
    && git checkout $PG_BRANCH \
    && ./configure \
    && cd /usr/src/postgres/contrib \
    && git clone https://github.com/ng-galien/pldebugger.git \
    && cd pldebugger \
    && git checkout $PLUGIN_BRANCH \
    && make clean  \
    && make USE_PGXS=1  \
    && make USE_PGXS=1 install \
    && rm -r /usr/src/postgres \
    && apt --yes remove --purge  \
        git build-essential  \
        libreadline-dev  \
        zlib1g-dev bison  \
        libkrb5-dev flex  \
        postgresql-server-dev-$PG_VERSION \
    && apt --yes autoremove  \
    && apt --yes clean

COPY *.sql /docker-entrypoint-initdb.d/
COPY *.sh /docker-entrypoint-initdb.d/
RUN chmod a+r /docker-entrypoint-initdb.d/*


