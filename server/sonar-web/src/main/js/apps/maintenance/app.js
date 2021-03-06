define([
  './main-view'
], function (MainView) {

  var App = new Marionette.Application();

  App.on('start', function (options) {
    var viewOptions = _.extend(options, {
      model: new Backbone.Model()
    });
    var mainView = new MainView(viewOptions);
    mainView.render().refresh();
  });

  return App;

});
