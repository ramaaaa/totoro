from coffin.conf.urls.defaults import *
from coffin.shortcuts import redirect
from django.contrib.auth.views import logout

from prototype.jinja2 import login
from django.conf.urls.defaults import patterns, include, url

from django.contrib import admin
admin.autodiscover()

#def smartlogin(request, **kwargs):
#    if request.user.is_authenticated() and 'next' not in request.GET:
#        return redirect('index')
#    return login(request, **kwargs)

urlpatterns = patterns('',
    url(r'^index$', 'prototype_app.views.index'),

	#url(r'^$', 'prototype_app.views.index', name='index'),

	url(r'^$', 'prototype_app.views.home'),
	url(r'^facebook/login$', 'facebook.views.login'),
	url(r'^facebook/authentication_callback$', 'facebook.views.authentication_callback'),
	#url(r'^logout$', 'django.contrib.auth.views.logout'),

	url(r'^admin/', include(admin.site.urls)),

    #url(r'^login/$', smartlogin, kwargs=dict(template_name='login.html'), name='login'),
    url(r'^logout/$', logout, kwargs=dict(next_page='/'), name='logout'),

)
