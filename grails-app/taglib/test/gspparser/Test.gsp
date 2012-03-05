<%@ page namespace="ns" import="be.ixor.grails.gsptaglib.TagLibCodeBlock" %>
<%@ page docs="
@attr name Required
" %>

<% @TagLibCodeBlock
   String injected
%>

<p>
    ${attrs.name}
    ${injected}
</p>