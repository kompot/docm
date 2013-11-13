rs.initiate({
_id: "{{ pillar['repl_set'] }}",
members: [
    {% for host in pillar['db_hosts'] %}
        { _id : {{ loop.index0 }}, host : "{{ host }}" },
    {% endfor %}
]
})
