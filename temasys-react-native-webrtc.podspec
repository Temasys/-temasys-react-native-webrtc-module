require 'json'

package = JSON.parse(File.read(File.join(__dir__, 'package.json')))

Pod::Spec.new do |s|
  s.name                = package['name']
  s.version             = package['version']
  s.summary             = package['description']
  s.homepage            = 'https://github.com/Temasys/temasys-react-native-webrtc-module.git'
  s.license             = package['license']
  s.author              = 'Temasys'
  s.source              = { :git => 'git@github.com:Temasys/temasys-react-native-webrtc-module.git', :tag => 'test' }
  s.requires_arc        = true

  s.platforms           = { :ios => '10.0' }

  s.preserve_paths      = 'ios/**/*'
  s.source_files        = 'ios/**/*.{h,m}'
  s.libraries           = 'c', 'sqlite3', 'stdc++'
  s.framework           = 'AudioToolbox','AVFoundation', 'CoreAudio', 'CoreGraphics', 'CoreVideo', 'GLKit', 'VideoToolbox'
  s.ios.vendored_frameworks   = 'ios/WebRTC.framework'
  s.xcconfig            = { 'OTHER_LDFLAGS' => '-framework WebRTC' }
  s.dependency          'React'
end
